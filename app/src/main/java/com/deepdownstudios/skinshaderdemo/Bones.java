package com.deepdownstudios.skinshaderdemo;

import android.util.Pair;

import com.deepdownstudios.skinshaderdemo.BasicModel.Animation;
import com.deepdownstudios.skinshaderdemo.BasicModel.Bone;
import com.deepdownstudios.skinshaderdemo.BasicModel.RigidTransform;
import com.deepdownstudios.skinshaderdemo.BasicModel.Skeleton;
import com.deepdownstudios.util.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Static methods for handling bones.  Defines abstract routines for dealing with bones
 * of varied internal format.
 */
public final class Bones {
    public interface GLSLBones {
        /**
         * Post a list of bones to GLES.  The format is specified by this object.
         * @param boneArrayId   The GLES ID of the uniform shader variable that represents
         *                      an array of bone transformations.
         */
        void postToGLSLUniform(int boneArrayId);
    }

    /**
     * Stores the inverse bind pose transformations in a new instance of the bone hierarchy.
     * @param bones The bone transformations in their bone-to-parent-bone-space bind configuration.
     * @return      A copy of the inverse bone transformations in their bone-to-model-space bind configuration.
     */
    static public List<Bone> calculateInvBindPose(List<Bone> bones) {
        List<Bone> ret = new ArrayList<>(bones.size());
        for (Bone bone : bones) {
            ret.add(bone.copy());
        }

        // Bones start as parent/bone (ie bone-to-parent-bone) transformations.

        // Compose the bone's transformations with a pre-order traversal of the skeleton.
        // Result will be bone/model (ie model-to-bone) transformations.
        // ("Model" space here is often referred to as skin space or skin-local space.)
        calculatePose(ret);

        // Invert the pose transformations to give a the inverse bind pose.
        // Result will be bone/model (ie bone-to-model) transformations.
        for (Bone bone : ret) {
            // Invert the transformation by transforming the translation with the inverse-rotation and
            // then inverting the angle and new translation.
            bone.transform.quat.values[0] *= -1.0;      // negating w is a tiny bit faster than computing the conjugate
            bone.transform.pos = bone.transform.quat.transformPoint(bone.transform.pos);
            bone.transform.pos[0] *= -1.0;
            bone.transform.pos[1] *= -1.0;
            bone.transform.pos[2] *= -1.0;
        }

        return ret;
    }

    /**
     * Calculate a model-space bind pose for a set of bones.  In other words, the calculated
     * bone transforms map from each bone's local bone-space to model-space.
     * @param bones         Bone structure, representing bone-to-parent transforms.
     *                      Changed on output to contain the bone-to-model-space transformations.
     */
    static public void calculatePose(List<Bone> bones) {
        calculatePose(bones, null, 0);
    }

    /**
     * Calculate a model-space pose for a set of bones and, optionally, an animation.
     * @param bones         Bone structure to animate, each in it's local bone-space.
     *                      Changed on output to contain the new pose in model space.
     * @param animation     Animation to apply or none to return default pose.
     * @param delta         Time of animation to evaluate, in seconds.  Ignored if animation == null.
     */
    static public void calculatePose(List<Bone> bones, Animation animation, double delta) {
        for (int i=0; i<bones.size(); i++) {
            // Since the bones are listed in preorder, this bone's parent has already
            // been processed.
            Bone bone = bones.get(i);

            // Joints with null keyframes are not individually animated.
            if (animation != null && animation.keyframes[i] != null) {
                // Compose the animation transformation on top of the bind pose transform.
                RigidTransform animTform = getTransformAtTime(animation.keyframes[i], delta);

                // Keyframes are a "unitless" transform -- the keyframe transformations define a
                // delta for the bone transformation -- they do not introduce a new coordinate
                // space.  So the bone.transform is still in parent/local-bone units.
                bone.transform = bone.transform.multiply(animTform);
            }

            if (bone.parentIdx == -1)
                continue;

            Bone parent = bones.get(bone.parentIdx);
            // MATH ALERT:
            // The parent has already had its transformation updated so it
            // maps from parent-bone-space to model space (model/parent).
            // bone.transform = parent.transform * bone.transform is
            // bone.transform = model/parent * parent/local-bone = model/local-bone
            // meaning the new transform maps from local bone coordinates to model coordinates.
            bone.transform = parent.transform.multiply(bone.transform);
        }
    }

    /**
     * An object that can store a list of bone transforms in its preferred form, whatever
     * that may be.
     */
    public interface TransformStorage {
        /**
         * Called before any transforms are stored.
         * @param nTransforms The number of transforms we will need room for.
         */
        void allocateStorage(int nTransforms);

        /**
         * Store `transform`, given as a RigidTransform (3D-translation + Quaternion) at index `transformIdx`.
         */
        void storeTransform(int transformIdx, RigidTransform transform);
    }

    /**
     * Calculate bone transformations and store them in a TransformStorage.
     * @param animation The animation to apply or null for the default pose.
     * @param skeleton  The skeleton of the model to animate
     * @param delta     The time in the animation to set as pose, in seconds.  Ignored if animation == null.
     * @param ts        Abstract storage for transforms.  Can be used to store matrices, quats, ...
     */
    static public void storeBoneTransforms(Animation animation, Skeleton skeleton, double delta, TransformStorage ts) {
        List<Bone> bones = skeleton.bones;
        ts.allocateStorage(bones.size());

        List<Bone> bonesCopy = new ArrayList<>();
        for (Bone bone: bones)
            bonesCopy.add(bone.copy());

        // Calculate the (potentially animated) bone-to-model matrices.
        calculatePose(bonesCopy, animation, delta);

        // MATH ALERT: Multiply the invBindPose transformations (which map model-to-bone-space, or bone/model)
        // by mTforms (bone-to-model or model/bone).  So
        // mTforms * invBindPose = model/bone * bone/model = a "unitless"
        // transformation that has no inherent coordinate system.  Which is good because
        // the skeletal transformation maps from a model space back into itself.
        // In the shader, you then also apply MVP as you do to position any object.
        for (int i=0; i<bonesCopy.size(); i++) {
            Bone bone = bonesCopy.get(i);
            Bone invBone = skeleton.invBindPose.get(i);
            RigidTransform product = bone.transform.multiply(invBone.transform);
            ts.storeTransform(i, product);
        }
    }

    static private RigidTransform getTransformAtTime(
            ArrayList<Pair<Double, RigidTransform>> keyframe, double delta) {

        // For animations that don't start at time 0 (I'm looking at you Milkshape), displace
        // delta by that amount because delta was based on duration, not some weird local
        // animation time.
        delta += keyframe.get(0).first;
        Util.Assert(delta <= keyframe.get(keyframe.size()-1).first);      // Must not exceed animation length!

        // Look for the first frame at time >= the time we want
        int afterFrame = Collections.binarySearch(keyframe, new Pair<Double, RigidTransform>(delta, null),
                new Comparator<Pair<Double, RigidTransform>>() {
                    @Override
                    public int compare(Pair<Double, RigidTransform> lhs, Pair<Double, RigidTransform> rhs) {
                        return lhs.first.compareTo(rhs.first);
                    }
                });

        if (afterFrame < 0) {
            // We weren't at an exact spot.  binarySearch returns -afterFrame-1 in that case.
            afterFrame = -(afterFrame + 1);
        }

        Util.Assert(afterFrame < keyframe.size());

        // Exact keyframe match
        if (keyframe.get(afterFrame).first == delta) {
            return keyframe.get(afterFrame).second.copy();
        }

        // Keyframe interpolation
        Pair<Double, RigidTransform> e1 = keyframe.get(afterFrame-1);
        Pair<Double, RigidTransform> e2 = keyframe.get(afterFrame);
        double weight = (delta - e1.first)/(e2.first - e1.first);
        Util.Assert(weight <= 1.0 && weight >= 0.0);
        RigidTransform eNew = new RigidTransform();
        eNew.quat = e1.second.quat.slerp(e2.second.quat, weight);
        for(int i=0; i<3; i++) {
            eNew.pos[i] = (1.0-weight) * e1.second.pos[i] + weight * e2.second.pos[i];
        }

        return eNew;
    }
}
