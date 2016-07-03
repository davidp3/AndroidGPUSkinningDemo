package com.deepdownstudios.skinshaderdemo;

import android.content.res.Resources;
import android.util.Pair;

import com.deepdownstudios.util.Util;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.deepdownstudios.skinshaderdemo.ByteBufferModel.Animation;
import static com.deepdownstudios.skinshaderdemo.ByteBufferModel.Bone;
import static com.deepdownstudios.skinshaderdemo.ByteBufferModel.Mesh;
import static com.deepdownstudios.skinshaderdemo.ByteBufferModel.RigidTransform;
import static com.deepdownstudios.skinshaderdemo.ByteBufferModel.Skeleton;
import static com.deepdownstudios.skinshaderdemo.ByteBufferModel.Vertex;

/**
 * Handles OGRE .mesh and .skeleton files.
 */
public class OgreModelSource implements Source<Model> {

    private Resources mResources;
    private Cache<GLESTexture> mTextureCache;
    private int mMeshResourceId;
    private int mSkelResourceId;
    private int mTextureResourceId;
    private int mBumpResourceId;

    /**
     * Create an instance of ... whatever this format is.
     * @param resources         The Android SDK Resource object for loading files.
     * @param textureCache      Cache of GLES texture IDs.
     * @param meshResourceId    The resource ID of the .mesh file
     * @param skelResourceId    The resource ID of the .skel file
     * @param textureResourceId The resource ID of the base texture Drawable.
     * @param bumpResourceId    The resource ID of the bump texture Drawable.
     */
    public OgreModelSource(Resources resources, Cache<GLESTexture> textureCache, int meshResourceId, int skelResourceId,
                           int textureResourceId, int bumpResourceId) {
        mResources = resources;
        mTextureCache = textureCache;
        mMeshResourceId = meshResourceId;
        mSkelResourceId = skelResourceId;
        mTextureResourceId = textureResourceId;
        mBumpResourceId = bumpResourceId;
    }

    @Override
    public Model load() {
        InputStream inputStream = mResources.openRawResource(mMeshResourceId);
        XmlPullParser meshXpp;
        try {
            meshXpp = XmlPullParserFactory.newInstance().newPullParser();
            meshXpp.setInput(inputStream, null);
        } catch (XmlPullParserException e) {
            throw new IllegalStateException("Failed to read mesh resource file : " + mMeshResourceId, e);
        }

        inputStream = mResources.openRawResource(mSkelResourceId);
        XmlPullParser skelXpp;
        try {
            skelXpp = XmlPullParserFactory.newInstance().newPullParser();
            skelXpp.setInput(inputStream, null);
        } catch (XmlPullParserException e) {
            throw new IllegalStateException("Failed to read skeleton resource file : " + mSkelResourceId, e);
        }

        ByteBufferModel bbModel;
        try {
            bbModel = loadBB(meshXpp, skelXpp);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build ByteBufferModel for mesh resource : " +
                    mMeshResourceId + " and skel resource : " + mSkelResourceId, e);
        }
        return new VBOModel(mResources, mTextureCache, bbModel);
    }

    private ByteBufferModel loadBB(XmlPullParser meshXpp, XmlPullParser skelXpp) throws IOException, XmlPullParserException {
        List<Mesh> meshes = readMeshes(meshXpp);
        Skeleton skeleton = readSkeleton(skelXpp);
        return new ByteBufferModel(meshes, skeleton);
    }

    static private Skeleton readSkeleton(XmlPullParser skelXpp) throws IOException, XmlPullParserException {
        Skeleton ret = new Skeleton();
        int curBone = 0;

        int eventType = skelXpp.next();
        Map<String, Integer> boneNameMap = new HashMap<>();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if(eventType == XmlPullParser.START_TAG) {
                if (skelXpp.getName().equals("bone")) {
                    // bone rest position stuff
                    Util.Assert(curBone == Integer.parseInt(skelXpp.getAttributeValue(null, "id")));
                    Bone bone = readBone(skelXpp);
                    ret.bones.add(bone);
                    boneNameMap.put(bone.name, curBone);
                    curBone++;
                } else if (skelXpp.getName().equals("boneparent")) {
                    // bone tree-hierarchy definition
                    String childName = skelXpp.getAttributeValue(null, "bone");
                    String parentName = skelXpp.getAttributeValue(null, "parent");
                    Util.Assert(boneNameMap.containsKey(parentName));
                    ret.bones.get(boneNameMap.get(childName)).parentIdx = boneNameMap.get(parentName);
                } else if (skelXpp.getName().equals("animation")) {
                    Animation animation = readAnimation(skelXpp, boneNameMap);
                    ret.animations.add(animation);
                }
            }
            eventType = skelXpp.next();
        }

        // I'm assuming that the format stores the root bone as bone #0.
        Util.Assert(ret.bones.get(0).parentIdx == -1);

        // I'm assuming that the format stores bones in a pre-ordering
        // (so parents are always before children).
        for (int i=1; i < ret.bones.size(); i++) {
            Util.Assert(i > ret.bones.get(i).parentIdx);
        }

        // The format stores the bones in their local (joint) space.
        // We calculate a new list that represents the inverse of the composed bone transforms...
        // the inverse bind pose.
        ret.invBindPose = Bones.calculateInvBindPose(ret.bones);

        return ret;
    }

    @SuppressWarnings("unchecked")
    static private Animation readAnimation(XmlPullParser skelXpp, Map<String, Integer> boneNameMap)
            throws IOException, XmlPullParserException {
        Animation ret = new Animation();
        ret.name = skelXpp.getAttributeValue(null, "name");
        ret.duration = Double.parseDouble(skelXpp.getAttributeValue(null, "length"));
        ret.keyframes = new ArrayList[boneNameMap.size()];

        int eventType = skelXpp.next();
        while (skelXpp.getName() == null || !skelXpp.getName().equals("animation")) {
            if(eventType == XmlPullParser.START_TAG) {
                if (skelXpp.getName().equals("track")) {
                    String boneName = skelXpp.getAttributeValue(null, "bone");
                    Util.Assert(boneNameMap.containsKey(boneName));
                    int jointIdx = boneNameMap.get(boneName);
                    Util.Assert(ret.keyframes[jointIdx] == null);
                    ret.keyframes[jointIdx] = readJointAnim(skelXpp);
                }
            }
            eventType = skelXpp.next();
        }

        Util.Assert(eventType == XmlPullParser.END_TAG);
        return ret;
    }

    static private ArrayList<Pair<Double, RigidTransform>> readJointAnim(XmlPullParser skelXpp)
            throws IOException, XmlPullParserException {
        ArrayList<Pair<Double, RigidTransform>> ret = new ArrayList<>();
        int eventType = skelXpp.next();
        double lastTime = -1.0;
        boolean needsSort = false;
        while (skelXpp.getName() == null || !skelXpp.getName().equals("track")) {
            if(eventType == XmlPullParser.START_TAG) {
                if (skelXpp.getName().equals("keyframe")) {
                    double time = Double.parseDouble(skelXpp.getAttributeValue(null, "time"));
                    needsSort |= (time <= lastTime);       // make sure key frames are sorted by time
                    lastTime = time;
                    RigidTransform transform = readRigidTransform(skelXpp);
                    ret.add(new Pair<>(time, transform));
                }
            }
            eventType = skelXpp.next();
        }

        // Sort by keyframe time if needed.
        if (needsSort) {
            Collections.sort(ret, new Comparator<Pair<Double, RigidTransform>>() {
                @Override
                public int compare(Pair<Double, RigidTransform> lhs, Pair<Double, RigidTransform> rhs) {
                    return lhs.first.compareTo(rhs.first);
                }
            });
        }

        Util.Assert(eventType == XmlPullParser.END_TAG);
        return ret;
    }

    static private Bone readBone(XmlPullParser skelXpp) throws IOException, XmlPullParserException {
        Bone ret = new Bone();
        ret.name = skelXpp.getAttributeValue(null, "name");
        ret.transform = readRigidTransform(skelXpp);
        ret.parentIdx = -1;
        return ret;
    }

    static private RigidTransform readRigidTransform(XmlPullParser skelXpp) throws XmlPullParserException, IOException {
        RigidTransform ret = new RigidTransform();
        double angle = 0.0, axis[] = new double[3];
        int eventType = skelXpp.next();
        while (skelXpp.getName() == null || (!skelXpp.getName().equals("bone") && !skelXpp.getName().equals("keyframe"))) {
            if(eventType == XmlPullParser.START_TAG) {
                // Strangely, the format uses "position" and "rotation" in the skeleton and
                // "translate" and "rotate" in keyframes.  But the data is the same.
                if (skelXpp.getName().equals("position") || skelXpp.getName().equals("translate")) {
                    ret.pos[0] = Double.parseDouble(skelXpp.getAttributeValue(null, "x"));
                    ret.pos[1] = Double.parseDouble(skelXpp.getAttributeValue(null, "y"));
                    ret.pos[2] = Double.parseDouble(skelXpp.getAttributeValue(null, "z"));
                } else if (skelXpp.getName().equals("rotation") || skelXpp.getName().equals("rotate")) {
                    angle = Double.parseDouble(skelXpp.getAttributeValue(null, "angle"));
                } else if (skelXpp.getName().equals("axis")) {
                    axis[0] = Double.parseDouble(skelXpp.getAttributeValue(null, "x"));
                    axis[1] = Double.parseDouble(skelXpp.getAttributeValue(null, "y"));
                    axis[2] = Double.parseDouble(skelXpp.getAttributeValue(null, "z"));
                }
            }
            eventType = skelXpp.next();
        }

        ret.quat = new Quaternion(angle, axis);

        Util.Assert(eventType == XmlPullParser.END_TAG);
        return ret;
    }

    private List<Mesh> readMeshes(XmlPullParser meshXpp) throws XmlPullParserException, IOException {
        List<Mesh> meshes = new ArrayList<>();
        int eventType = meshXpp.next();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if(eventType == XmlPullParser.START_TAG) {
                if (meshXpp.getName().equals("submesh")) {
                    meshes.add(readMesh(meshXpp));
                }
                // I'm ignoring the LOD info but, if I cared, I would catch
                // "lodfacelist" here and read them into meshes using the "faces" code
                // from addMesh.
                // I'm also ignoring the submesh "material" attribute, which lists a simple property
                // file with data.  I'm using ModelData.java instead.
            }
            eventType = meshXpp.next();
        }
        return meshes;
    }

    private Mesh readMesh(XmlPullParser meshXpp) throws IOException, XmlPullParserException {
        Mesh ret = new Mesh();
        ret.mMaterial.textureResourceId = mTextureResourceId;
        ret.mMaterial.bumpResourceId = mBumpResourceId;

        int curFace = 0;
        int curVert = 0;
        int nFaces = -1;
        int nVerts = -1;

        int eventType = meshXpp.next();
        while (meshXpp.getName() == null || !meshXpp.getName().equals("submesh")) {
            if(eventType == XmlPullParser.START_TAG) {
                if (meshXpp.getName().equals("faces")) {
                    Util.Assert(nFaces == -1);
                    nFaces = Integer.parseInt(meshXpp.getAttributeValue(null, "count"));
                    ret.faces = new short[nFaces][3];
                } else if (meshXpp.getName().equals("face")) {
                    ret.faces[curFace][0] = Short.parseShort(meshXpp.getAttributeValue(null, "v1"));
                    ret.faces[curFace][1] = Short.parseShort(meshXpp.getAttributeValue(null, "v2"));
                    ret.faces[curFace][2] = Short.parseShort(meshXpp.getAttributeValue(null, "v3"));
                    curFace++;
                } else if (meshXpp.getName().equals("geometry")) {
                    Util.Assert(nVerts == -1);
                    nVerts = Integer.parseInt(meshXpp.getAttributeValue(null, "vertexcount"));
                    ret.verts = new Vertex[nVerts];
                    for (int i=0; i<nVerts; i++) {
                        ret.verts[i] = new Vertex();
                    }
                } else if (meshXpp.getName().equals("position")) {
                    ret.verts[curVert].pos[0] = Double.parseDouble(meshXpp.getAttributeValue(null, "x"));
                    ret.verts[curVert].pos[1] = Double.parseDouble(meshXpp.getAttributeValue(null, "y"));
                    ret.verts[curVert].pos[2] = Double.parseDouble(meshXpp.getAttributeValue(null, "z"));
                } else if (meshXpp.getName().equals("normal")) {
                    ret.verts[curVert].normal[0] = Double.parseDouble(meshXpp.getAttributeValue(null, "x"));
                    ret.verts[curVert].normal[1] = Double.parseDouble(meshXpp.getAttributeValue(null, "y"));
                    ret.verts[curVert].normal[2] = Double.parseDouble(meshXpp.getAttributeValue(null, "z"));
                } else if (meshXpp.getName().equals("texcoord")) {
                    ret.verts[curVert].texCoords[0] = Double.parseDouble(meshXpp.getAttributeValue(null, "u"));
                    ret.verts[curVert].texCoords[1] = Double.parseDouble(meshXpp.getAttributeValue(null, "v"));
                } else if (meshXpp.getName().equals("vertexboneassignment")) {
                    int vertIdx = Integer.parseInt(meshXpp.getAttributeValue(null, "vertexindex"));
                    ret.verts[vertIdx].boneWeights.add(Double.parseDouble(meshXpp.getAttributeValue(null, "weight")));
                    ret.verts[vertIdx].bones.add(Integer.parseInt(meshXpp.getAttributeValue(null, "boneindex")));
                }
            } else if(eventType == XmlPullParser.END_TAG) {
                if (meshXpp.getName().equals("vertexbuffer")) {
                    // The vertexbuffers in each "submesh" all correspond to
                    // the same vertices.  So, when one vertex buffer ends,
                    // reset the count so that the next one can augment
                    // those verts.
                    curVert = 0;
                } else if (meshXpp.getName().equals("vertex")) {
                    curVert++;
                }
            }
            eventType = meshXpp.next();
        }

        Util.Assert(eventType == XmlPullParser.END_TAG);        // we done with "submesh"
        return ret;
    }
}
