package com.deepdownstudios.skinshaderdemo;

import android.content.res.Resources;
import android.util.Log;
import android.util.Pair;

import com.deepdownstudios.skinshaderdemo.BasicModel.Material;
import com.deepdownstudios.skinshaderdemo.BasicModel.RenderPass;
import com.deepdownstudios.util.LittleEndianDataInputStream;
import com.deepdownstudios.util.Util;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.deepdownstudios.skinshaderdemo.BasicModel.Animation;
import static com.deepdownstudios.skinshaderdemo.BasicModel.Bone;
import static com.deepdownstudios.skinshaderdemo.BasicModel.Mesh;
import static com.deepdownstudios.skinshaderdemo.BasicModel.RigidTransform;
import static com.deepdownstudios.skinshaderdemo.BasicModel.Skeleton;
import static com.deepdownstudios.skinshaderdemo.BasicModel.Vertex;

/**
 * MilkShape 3D importer.
 * Based on the MS3D 1.8.5 spec:
 * https://gist.githubusercontent.com/sapper-trle/db1dc6670ec0fa733d7d/raw/17899c1004bd896e3f9049b3dc181f9070ae6cbb/ms3dspec.txt.c
 */
public class Ms3dModelSource implements Source<Model> {

    /**
     * Milkshape skinned import.
     * @param textureCache    Cache of GLESTextures
     * @param animFrameRanges A list of [start,end] frame ranges (inclusive).
     */
    public Ms3dModelSource(Resources resources, Cache<GLESTexture> textureCache,
                           int resourceId, List<Pair<Integer, Integer>> animFrameRanges, double speed) {
        mResources = resources;
        mTextureCache = textureCache;
        mResourceId = resourceId;
        mAnimFrameRanges = animFrameRanges;
        mSpeed = speed;
    }

    @Override
    public Model load() {
        LittleEndianDataInputStream stream =
                new LittleEndianDataInputStream(new BufferedInputStream(mResources.openRawResource(mResourceId)));
        ByteBufferModel bbModel;
        try {
            bbModel = loadBB(stream);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read ms3d resource file: " + mResourceId, e);
        }
        return new VBOModel(mResources, mTextureCache, bbModel);
    }

    @SuppressWarnings("unused")
    private ByteBufferModel loadBB(LittleEndianDataInputStream stream) throws IOException {
        readHeader(stream);
        short nVerts = stream.readShort();
        Vertex[] vertices = new Vertex[nVerts];
        double[] minV = { Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE };
        double[] maxV = { Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE };

        // TEST:
        for (int i=0; i<nVerts; i++) {
            vertices[i] = readVertex(stream);
            for (int j=0; j<3; j++) {
                if (minV[j] > vertices[i].pos[j]) {
                    minV[j] = vertices[i].pos[j];
                }
                if (maxV[j] < vertices[i].pos[j]) {
                    maxV[j] = vertices[i].pos[j];
                }
            }
        }

        short nFaces = stream.readShort();
        short[][] faces = new short[nFaces][];
        for (int i=0; i<nFaces; i++) {
            faces[i] = readFace(stream, vertices);
        }
        short nMeshes = stream.readShort();
        ArrayList<Mesh> meshes = new ArrayList<>();
        ArrayList<Integer> meshMaterials = new ArrayList<>();
        for (int i=0; i<nMeshes; i++) {
            Pair<Integer, Mesh> matAndMesh = readMesh(stream, vertices, faces);
            meshMaterials.add(matAndMesh.first);
            meshes.add(matAndMesh.second);
        }
        short nMaterials = stream.readShort();
        Material[] materials = new Material[nMaterials];
        for (int i=0; i<nMaterials; i++) {
            Material material = new Material();
            String materialName = readString(stream, 32, "material name");
            for (int j=0; j<4; j++) {
                material.ambient[j] = stream.readFloat();
            }
            for (int j=0; j<4; j++) {
                material.diffuse[j] = stream.readFloat();
            }
            for (int j=0; j<4; j++) {
                material.specular[j] = stream.readFloat();
            }
            for (int j=0; j<4; j++) {
                material.emissive[j] = stream.readFloat();
            }
            material.shininess = stream.readFloat();
            material.transparency = stream.readFloat();
            stream.skipBytes(1);        // unused
            String textureName = readString(stream, 128 /* number of chars */, "base texture name");
            textureName = stripFilename(textureName);
            Log.d(TAG, "Found texture " + textureName + " in material " + materialName);
            // Look for the texture in the same resource package as the model file.
            // (This is probably the Application package.)
            material.textureResourceId =
                    mResources.getIdentifier(textureName, "drawable",
                            mResources.getResourcePackageName(mResourceId));
            readString(stream, 128 /* number of chars */, "alpha texture name");  // ignored

            materials[i] = material;
        }
        for (int i=0; i<meshes.size(); i++) {
            RenderPass pass = new RenderPass();
            pass.material = materials[meshMaterials.get(i)];
            meshes.get(i).mRenderPasses.add(pass);
        }

        // Fps is not defined by the spec and is redundant since frame times are given in seconds.
        // However, the creator of the "Ninja" model specifies that his animations are obtained
        // by numbers of keyframes... even though keyframes are not numbered (they have times, in seconds).
        // It might be the case that his file coincidentally has numbered keyframes
        // (ie all rot/trans frames have coincidental times) but I'm going a different way.
        // I assume that a "frame" is defined by the fps term and the frame # at a given time
        // is fps*time_in_seconds.
        float fps = stream.readFloat();
        Util.Assert(fps > 0);
        float curTime = stream.readFloat();       // Poorly defined by the spec.  Ignore.
        int nFrames = stream.readInt();           // Poorly defined by the spec.  Ignore.

        // Read the skeleton along with the entire keyframe list as one animation.
        Skeleton skeleton = readSkeletonAndAnimation(stream, vertices, fps);

        // Now break the "one animation" into multiple animations based on mAnimFrameRanges and fps.
        skeleton.animations = splitAnimation(skeleton.animations.get(0), mAnimFrameRanges, fps, mSpeed);
        return new ByteBufferModel(meshes, skeleton);
    }

    /**
     * Split animation into multiple animations.
     * @param totalAnimation    The total animation.  Its keyframes must be sorted by time.
     * @param animFrameRanges   List of (Start,End) keyframes for the animations to create.
     * @param fps               Frames per second, used to determine which frame times
     *                          correspond to which frame numbers.
     * @return              The animations we split animation into.
     */
    @SuppressWarnings("unchecked")
    static private List<Animation> splitAnimation(Animation totalAnimation,
                                                  List<Pair<Integer, Integer>> animFrameRanges,
                                                  float fps, double speed) {
        int nBones = totalAnimation.keyframes.length;
        // I'm not currently resampling animations because that really shouldn't be necessary (I
        // assume the animations start/end on keyframes) and because I don't want to introduce
        // precision errors.  If assumptions prove untrue then I may need to revisit this choice.
        List<Animation> animations = new ArrayList<>(animFrameRanges.size());
        for (Pair<Integer,Integer> range : animFrameRanges) {
            // Since I am not resampling, EPSILON allows me to search for the calculated
            // frame times and assume that the _next_ keyframe is the one we want.
            // This isn't exactly robust but you would need an errant frame
            // 1/1000th of a second back for this to produce incorrect results.  That would
            // be super weird.
            final double EPSILON = 0.001;
            double startTime = (double)range.first / fps - EPSILON;
            double endTime = (double)range.second / fps - EPSILON;
            if (startTime > totalAnimation.duration || endTime > totalAnimation.duration) {
                throw new IllegalArgumentException("Animation frames (" + range.first + ", " +
                        range.second + ") correspond to times (" + startTime + ", " + endTime +
                        ") which exceed total animation length " + totalAnimation.duration);
            }

            Animation animation = new Animation();
            animation.duration = (endTime-startTime) / speed;
            animation.name = "<" + range.first + "-" + range.second + ">";      // name is frame range
            animation.keyframes = new ArrayList[nBones];
            for (int boneIdx=0; boneIdx<nBones; boneIdx++) {
                if (totalAnimation.keyframes[boneIdx] == null) {
                    continue;       // leave as a null bone channel
                }
                int startFrame = Collections.binarySearch(totalAnimation.keyframes[boneIdx],
                    new Pair<Double, RigidTransform>(startTime, null),
                        new Comparator<Pair<Double, RigidTransform>>() {
                            @Override
                            public int compare(Pair<Double, RigidTransform> lhs, Pair<Double, RigidTransform> rhs) {
                                return lhs.first.compareTo(rhs.first);
                            }
                        });
                int endFrame = Collections.binarySearch(totalAnimation.keyframes[boneIdx],
                        new Pair<Double, RigidTransform>(endTime, null),
                        new Comparator<Pair<Double, RigidTransform>>() {
                            @Override
                            public int compare(Pair<Double, RigidTransform> lhs, Pair<Double, RigidTransform> rhs) {
                                return lhs.first.compareTo(rhs.first);
                            }
                        });

                if (startFrame < 0) {
                    // We weren't at an exact spot.  binarySearch returns -frame-1 in that case.
                    startFrame = -(startFrame + 1);
                }
                if (endFrame < 0) {
                    endFrame = -(endFrame + 1);
                }

                // Make sure frames exists (they should)
                Util.Assert(startFrame < totalAnimation.keyframes[boneIdx].size());
                Util.Assert(endFrame < totalAnimation.keyframes[boneIdx].size());

                List<Pair<Double, RigidTransform>> sublist =
                        totalAnimation.keyframes[boneIdx].subList(startFrame, endFrame+1);
                animation.keyframes[boneIdx] = new ArrayList<>(sublist.size());
                for (Pair<Double, RigidTransform> frame : sublist) {
                    animation.keyframes[boneIdx].add(new Pair<>(frame.first / speed, frame.second));
                }
            }
            animations.add(animation);
        }
        return animations;
    }

    @SuppressWarnings("unchecked,unused")
    static private Skeleton readSkeletonAndAnimation(LittleEndianDataInputStream stream,
                                                     Vertex[] vertices, float fps) throws IOException {
        Animation animation = new Animation();
        animation.name = "<all>";       // a temporary dummy animation
        Map<String, Bone> boneMap = new HashMap<>();
        Map<Bone, Integer> boneIdxMap = new HashMap<>();
        double duration = 0.0;

        short nBones = stream.readShort();
        List<Bone> bones = new ArrayList<>();
        animation.keyframes = new ArrayList[nBones];

        for (int i=0; i<nBones; i++) {
            Bone bone = readBone(stream, boneMap, boneIdxMap);
            bones.add(bone);
            boneIdxMap.put(bone, i);
            animation.keyframes[i] = readKeyframes(stream, fps);
            if (duration > 0.0) {
                if (animation.keyframes[i] != null &&
                        duration != animation.keyframes[i].get(animation.keyframes[i].size()-1).first -
                                animation.keyframes[i].get(0).first) {
                    throw new IllegalStateException("Already found keyframe track that ran " +
                            duration + " seconds but track " + i + " runs " +
                            animation.keyframes[i].get(animation.keyframes[i].size()-1).first +
                            " seconds.");
                }
            } else if (animation.keyframes[i] != null) {
                duration = animation.keyframes[i].get(animation.keyframes[i].size()-1).first -
                        animation.keyframes[i].get(0).first;
            }
        }
        animation.duration = duration;

        Skeleton skeleton = new Skeleton();
        skeleton.bones = bones;
        skeleton.animations.add(animation);

        // If we are at the EOF then we have an "old files" (see the "spec")
        boolean hasMoreData = true;
        try {
            int version = stream.readInt();               // Ignored.
        } catch(EOFException e) {
            hasMoreData = false;
            // All vertices will be left with one bone.  Give that bone a weight of 1.0.
            setSingleBoneWeights(vertices);
        }

        if (hasMoreData) {
            // Skip comments
            skipAllComments(stream);

            // Read extra vertex skeleton data
            readExtraVertexInfo(stream, vertices);

            // Ignore the rest of the file (joint colors and what-not)
        }

        skeleton.invBindPose = Bones.calculateInvBindPose(bones);
        return skeleton;
    }

    private static void setSingleBoneWeights(Vertex[] vertices) {
        for (Vertex v : vertices) {
            v.boneWeights.add(1.0);
        }
    }

    private static void readExtraVertexInfo(LittleEndianDataInputStream stream, Vertex[] vertices) throws IOException {
        int version = stream.readInt();
        if (version > 3) {
            throw new IllegalArgumentException("Unknown vertex extra information version number.  Required <= 3.  Found: " + version);
        }
        for (Vertex vert : vertices) {
            List<Integer> ids = vert.bones;     // already has one bone entry
            List<Double> weights = vert.boneWeights;
            double w0 = 1.0;
            weights.add(w0);        // temporarily set 0th bone's weight to 1.0

            for (int j = 0; j < 3; j++) {
                byte id = stream.readByte();
                if (id == -1)
                    continue;       // no bone
                ids.add((int) id);
            }
            for (int j = 0; j < 3; j++) {
                byte weightB = stream.readByte();
                if (j >= ids.size() - 1)      // no bone
                    continue;
                double weight = (double) weightB / 100.0;
                weights.add(weight);
                w0 -= weight;
            }
            weights.set(0, w0);     // update 0th bone's weight
            stream.skipBytes(NUM_BYTES_PER_INT * (version - 1));
        }
    }

    @SuppressWarnings("unused")         // for Ignored data
    private static void skipAllComments(LittleEndianDataInputStream stream) throws IOException {
        int nGroupComments = stream.readInt();
        skipNComments(stream, nGroupComments);
        int nMaterialComments = stream.readInt();
        skipNComments(stream, nMaterialComments);
        int nJointComments = stream.readInt();
        skipNComments(stream, nJointComments);
        int nModelComments = stream.readInt();
        skipNComments(stream, nModelComments);
    }

    @SuppressWarnings("unused")         // for Ignored data
    private static void skipNComments(LittleEndianDataInputStream stream, int nComments) throws IOException {
        for (int i=0; i<nComments; i++) {
            int index = stream.readInt();               // Ignored.
            int commentLen = stream.readInt();
            stream.skipBytes(commentLen);
        }
    }

    static private ArrayList<Pair<Double, RigidTransform>> readKeyframes(
            LittleEndianDataInputStream stream, float fps) throws IOException {
        short nRot = stream.readShort();
        short nTrans = stream.readShort();
        if (nRot == 0 && nTrans == 0) {
            return null;        // null keyframe list is interpreted as using bind-pose transform only
        }
        // This is funky.  The file does not require each keyframe time to have a trans and a rot, just
        // either-or.  We can interpolate to fill in the missing values... but this is hard without having
        // all of the data read in up front.  So do that.
        Map<Double, Quaternion> timeToKeyframeRot = new HashMap<>();
        Map<Double, double[]> timeToKeyframePos = new HashMap<>();
        Set<Double> timesSet = new HashSet<>();

        List<Double> rotTimes = new ArrayList<>();
        List<Double> transTimes = new ArrayList<>();
        for (int i=0; i<nRot; i++) {
            double time = stream.readFloat();
            float[] eulerAngles = new float[3];
            for (int j=0; j<3; j++) {
                eulerAngles[j] = stream.readFloat();
            }
            timeToKeyframeRot.put(time, new Quaternion(eulerAngles[0], eulerAngles[1], eulerAngles[2]));
            timesSet.add(time);
            rotTimes.add(time);
        }
        for (int i=0; i<nTrans; i++) {
            double time = stream.readFloat();
            double[] pos = new double[3];
            for (int j=0; j<3; j++) {
                pos[j] = (double)stream.readFloat();
            }
            timeToKeyframePos.put(time, pos);
            timesSet.add(time);
            transTimes.add(time);
        }

        Collections.sort(rotTimes);
        Collections.sort(transTimes);
        Double[] times = timesSet.toArray(new Double[timesSet.size()]);
        Arrays.sort(times);

        // Sanity check that translations and rotations run to the same time.
        if (!timeToKeyframePos.containsKey(times[times.length-1]) ||
                !timeToKeyframeRot.containsKey(times[times.length-1])) {
            throw new IllegalStateException("Keyframe at animation end time missing from either position or rotation channel.");
        }

        return buildKeyframes(timeToKeyframeRot, timeToKeyframePos, rotTimes, transTimes, times, fps);
    }

    /**
     * Sample given frames at evenly spaced times, based on fps.  The results will line up
     * with mAnimFrameRanges.
     */
    private static ArrayList<Pair<Double, RigidTransform>> buildKeyframes(
            Map<Double, Quaternion> timeToKeyframeRot, Map<Double, double[]> timeToKeyframePos,
            List<Double> rotTimes, List<Double> transTimes, Double[] times, float fps) {

        ArrayList<Pair<Double, RigidTransform>> ret = new ArrayList<>();
        double duration = times[times.length-1] - times[0];
        int ri = 0, ti = 0;     // always points to the _next_ index for rot/trans times
        for (int i=0; (double)i/fps <= duration; i++) {
            double frameTime = (double)i/fps + times[0];

            RigidTransform transform = new RigidTransform();

            while (rotTimes.get(ri+1) < frameTime) {
                ri++;
            }
            Util.Assert(rotTimes.get(ri) <= frameTime);
            double weight = (frameTime - rotTimes.get(ri)) / (rotTimes.get(ri+1) - rotTimes.get(ri));
            transform.quat =
                    timeToKeyframeRot.get(rotTimes.get(ri))
                            .slerp(timeToKeyframeRot.get(rotTimes.get(ri+1)), weight);

            while (transTimes.get(ti+1) < frameTime) {
                ti++;
            }
            Util.Assert(transTimes.get(ti) <= frameTime);
            weight =
                    (frameTime - transTimes.get(ti)) / (transTimes.get(ti+1) - transTimes.get(ti));
            for (int j = 0; j < 3; j++) {
                transform.pos[j] =
                        (1.0-weight) * timeToKeyframePos.get(transTimes.get(ti))[j] +
                             weight * timeToKeyframePos.get(transTimes.get(ti+1))[j];
            }

            ret.add(new Pair<>(frameTime, transform));
        }
        return ret;
    }

    @SuppressWarnings("unused")         // for Ignored data
    static private Bone readBone(LittleEndianDataInputStream stream, Map<String, Bone> boneMap,
                          Map<Bone, Integer> boneIdxMap) throws IOException {
        Bone ret = new Bone();
        byte flags = stream.readByte();         // Poorly defined by the spec.  Ignore.
        ret.name = readString(stream, 32 /* number of chars */, "bone name");
        boneMap.put(ret.name, ret);

        // empty parent name indicates root bone
        String boneParentName = readString(stream, 32, "bone parent name");
        if (!boneParentName.isEmpty()) {
            Bone parent = boneMap.get(boneParentName);
            // Here I am assuming that the MS3D file lists the bones in a pre-order so the parent has
            // already been found.  If not, throw an exception.
            if (parent == null) {
                throw new IllegalStateException("I assumed the MS3D file listed bones in a pre-order.  I was wrong.");
            }
            ret.parentIdx = boneIdxMap.get(parent);
        } else {
            ret.parentIdx = -1;
        }

        float[] eulerAngles = new float[3];
        for (int i=0; i<3; i++) {
            eulerAngles[i] = stream.readFloat();
        }
        ret.transform = new RigidTransform();
        ret.transform.quat = new Quaternion(eulerAngles[0], eulerAngles[1], eulerAngles[2]);
        for (int i=0; i<3; i++) {
            ret.transform.pos[i] = stream.readFloat();
        }
        return ret;
    }

    private static String readString(LittleEndianDataInputStream stream, int nChars,
                                     String description) throws IOException {
        byte[] strB = new byte[nChars];
        int nBytes = stream.read(strB, 0, nChars);
        int zeroIdx;
        if (nBytes != nChars) {
            throw new IllegalStateException("Attempt to read " + description +
                    ".  Required " + nChars + " bytes.  Found " + nBytes + " bytes.");
        }

        for (zeroIdx=0; zeroIdx<nChars && strB[zeroIdx] != 0; zeroIdx++) { }

        return new String(strB, 0, zeroIdx, "US-ASCII");
    }

    /**
     * Read the info for the meshes and build them from the lists of vertices/faces.
     * @param stream        The ms3d file at the mesh.flags field.
     * @param vertices      Indexed list of all vertices in all meshes.
     * @param faces         Indexed array of all triangles in all meshes.
     * @return              The new mesh.  Faces are new objects with indices mapped into the new Vertex list.
     *                      The Vertices, however, are the same objects in vertices.
     * @throws IOException
     */
    @SuppressWarnings("unused")         // for Ignored data
    static private Pair<Integer, Mesh> readMesh(LittleEndianDataInputStream stream, Vertex[] vertices, short[][] faces) throws IOException {
        Mesh ret = new Mesh();
        ArrayList<Vertex> meshVertices = new ArrayList<>();
        ArrayList<short[]> meshFaces = new ArrayList<>();
        Map<Short, Short> vertMap = new HashMap<>();

        // ms3d makes us pull the meshes verts/faces from the list of all verts/faces we are given.
        byte flags = stream.readByte();           // Poorly defined by the spec.  Ignore.
        byte[] nameB = new byte[32];
        int nBytes = stream.read(nameB, 0, 32);
        if (nBytes != 32) {
            throw new IllegalStateException("Attempt to read mesh name.  Required 32 bytes.  Found " + nBytes + " bytes.");
        }
        String meshName = new String(nameB);        // I don't really use this.
        short nFaces = stream.readShort();
        for (int i=0; i<nFaces; i++) {
            short faceIdx = stream.readShort();
            short[] face = faces[faceIdx];
            short[] meshFace = new short[3];
            for (int j=0; j<3; j++) {
                if (!vertMap.containsKey(face[j])) {
                    meshVertices.add(vertices[face[j]]);
                    vertMap.put(face[j], (short)(meshVertices.size()-1));
                    meshFace[j] = (short)(meshVertices.size()-1);
                } else {
                    meshFace[j] = vertMap.get(face[j]);
                }
            }
            meshFaces.add(meshFace);
        }
        byte material = stream.readByte();
        ret.verts = meshVertices.toArray(new Vertex[meshVertices.size()]);
        ret.faces = meshFaces.toArray(new short[meshFaces.size()][]);
        return new Pair<>((int)material, ret);
    }

    @SuppressWarnings("unused")         // for Ignored data
    static private short[] readFace(LittleEndianDataInputStream stream, Vertex[] vertices) throws IOException {
        short ret[] = new short[3];
        short flags = stream.readShort();           // Poorly defined by the spec.  Ignore.
        for (int i=0; i<3; i++) {
            ret[i] = stream.readShort();            // vertex indices
        }
        // Ms3d is weird in that the vertex normals and texture coordinates are defined for each face that the
        // vertex is part of, instead of one per vertex.  I'm speculating that most files ignore
        // this nonsense and use the same value for each vertex.  ...but I also test that theory here.
        double[] normal = new double[3];
        boolean[] wasDefined = new boolean[3];     // was the vertex normal of vertex[ret[i]] defined by a previous face?
        for (int i=0; i<3; i++) {
            Vertex v = vertices[ret[i]];
            for (int j=0; j<3; j++) {
                normal[j] = stream.readFloat();            // vertex normals
                wasDefined[i] |= v.normal[j] != 0.0f;
            }
            if (wasDefined[i]) {
                //noinspection PointlessBooleanExpression,ConstantConditions
                for (int j=0; TEST_VERTEX_NORMALS_AND_TCS && j<3; j++) {
                    if (v.normal[j] != normal[j]) {
                        throw new IllegalStateException("Vertex normal was: (" +
                                v.normal[0] + ", " + v.normal[1] + ", " + v.normal[2] +
                                ") but found new normal (" +
                                normal[0] + ", " + normal[1] + ", " + normal[2] +
                                ") at later face.");
                    }
                }
            } else {
                System.arraycopy(normal, 0, v.normal, 0, 3);
            }
        }

        for (int i=0; i<2; i++) {
            for (int j=0; j<3; j++) {
                Vertex v = vertices[ret[j]];
                double texC = stream.readFloat();
                //noinspection PointlessBooleanExpression
                if (TEST_VERTEX_NORMALS_AND_TCS && wasDefined[j]) {
                    if (v.texCoords[i] != texC) {
                        throw new IllegalStateException("Vertex texture coordinate was: " +
                                v.texCoords[i] +
                                " but found new texture coordinate " +
                                texC +
                                " at later face.");
                    }
                } else {
                    v.texCoords[i] = texC;
                }
            }
        }

        byte smoothingGroup = stream.readByte();        // spec says "1-32", which probably means something to someone.  I mean... come on.  Ignore.
        byte group = stream.readByte();        // Poorly defined by the spec.  Ignore.
        return ret;
    }

    @SuppressWarnings("unused")         // for Ignored data
    static private Vertex readVertex(LittleEndianDataInputStream stream) throws IOException {
        Vertex ret = new Vertex();
        byte flags = stream.readByte();     // Poorly defined by the spec.  Ignore.
        for (int i=0; i<3; i++) {
            ret.pos[i] = (double)stream.readFloat();
        }
        byte bone = stream.readByte();
        if (bone < 0) {
            throw new IllegalStateException("Vertex with no bone found.  We currently do not support unanimated meshes.");
        }
        ret.bones.add((int)bone);
        byte refCount = stream.readByte();      // Poorly defined by the spec.  Ignore.
        return ret;
    }

    private void readHeader(LittleEndianDataInputStream stream) throws IOException {
        byte[] headerB = new byte[10];
        int nBytes = stream.read(headerB, 0, 10);
        if (nBytes != 10) {
            throw new IllegalStateException("MS3D header invalid.  Required 10 bytes.  Found " + nBytes + " bytes.");
        }
        String header = new String(headerB);
        if(!header.equals("MS3D000000")) {
            throw new IllegalStateException("MS3D header expected: 'MS3D000000' -- found: '" + header + "'");
        }
        int version = stream.readInt();
        if(version > 4) {
            Log.w(TAG, "MS3D version may not be supported.  Max supported: 4.  Found: " + version);
        }
    }

    static private String stripFilename(String filename) {
        filename = filename.toLowerCase();
        int slashIdx = filename.lastIndexOf('/');
        if (slashIdx == -1) {
            slashIdx = filename.lastIndexOf('\\');
        }
        if (slashIdx != -1) {
            filename = filename.substring(slashIdx + 1);
        }
        int dotIdx = filename.indexOf('.');
        if (dotIdx != -1) {
            filename = filename.substring(0, dotIdx);
        }
        return filename;
    }

    private static final String TAG = "Ms3dModelSource";
    private Resources mResources;
    private Cache<GLESTexture> mTextureCache;
    private int mResourceId;
    /// "Sorted" list of animations, defined by start/end keyframe (sorted by start keyframe #).
    private List<Pair<Integer, Integer>> mAnimFrameRanges;
    private double mSpeed;

    /// We assume that per-face normal/texturing properties are really per-vertex as they
    /// are in most formats.  This tests that theory on ingest and throws an exception
    /// if it proves to be wrong.
    private static final boolean TEST_VERTEX_NORMALS_AND_TCS = true;
    private static final int NUM_BYTES_PER_INT = 4;
}
