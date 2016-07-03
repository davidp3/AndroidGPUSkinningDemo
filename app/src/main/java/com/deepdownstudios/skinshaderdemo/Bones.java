package com.deepdownstudios.skinshaderdemo;

import android.util.Pair;

import com.deepdownstudios.skinshaderdemo.ByteBufferModel.Animation;
import com.deepdownstudios.skinshaderdemo.ByteBufferModel.Bone;
import com.deepdownstudios.skinshaderdemo.ByteBufferModel.RigidTransform;
import com.deepdownstudios.util.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Universal and abstract concept of a list of bones.  Specific implementations operate with
 * their own encodings of key parts of the mathematics.
 */
public abstract class Bones {
    /**
     * Post the list to GLES.
     * @param boneArrayId   The GLES ID of the uniform shader variable that represents
     *                      an array of bone matricies.
     */
    public abstract void postToGLSLUniform(int boneArrayId);

    /**
     * Stores the inverse bind pose transformations in a new instance of the bone hierarchy.
     * @param bones The bone transformations in their parent-bone-to-bone-space bind configuration.
     * @return      A copy of the inverse bone transformations in their bone-to-model-space bind configuration.
     */
    static List<Bone> calculateInvBindPose(List<Bone> bones) {
        List<Bone> ret = new ArrayList<>(bones.size());
        for (Bone bone : bones) {
            ret.add(bone.copy());
        }

        // Bones start as parent/bone (ie bone-to-parent) transformations.

        // Compose the bone's transformations with a pre-order traversal of the skeleton.
        // Result will be model/bone (ie bone-to-model) transformations.
        // ("Model" space here is often referred to as skin space or skin-local space.)
        calculatePose(ret);

        // Invert the pose transformations to give a the inverse bind pose.
        // Result will be bone/model (ie model-to-bone) transformations.
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
    static void calculatePose(List<Bone> bones) {
        calculatePose(bones, null, 0);
    }

    /**
     * Calculate a model-space pose for a set of bones and, optionally, an animation.
     * @param bones         Bone structure to animate, each in it's local bone-space.
     *                      Changed on output to contain the new pose in model space.
     * @param animation     Animation to apply or none to return default pose.
     * @param delta         Time of animation to evaluate, in seconds.  Ignored if animation == null.
     */
    static void calculatePose(List<Bone> bones, Animation animation, double delta) {
        for (int i=0; i<bones.size(); i++) {
            // Since the bones are listed in preorder, this bone's parent has already
            // been processed.
            Bone bone = bones.get(i);

            // Joints with null keyframes are not individually animated.
            if (animation != null && animation.keyframes[i] != null) {
                // Compose the animation transformation on top of the bind pose transform.
                RigidTransform animTform = getTransformAtTime(animation.keyframes[i], delta);

                // MATH ALERT: The animation's keyframes define bone position relative to
                // the parent's position but *not in the parent's coordinate
                // frame*!!!  The position is instead
                // in the root node's coordinates.  In other words, the positions should be translated
                // but not rotated to align with it's coordinate frame.  This was
                // probably natural in the modeling tool as it makes sense for an animator.
                // Otherwise, this would be a simple RigidTransform.multiply().
                bone.transform = bone.transform.animCompose(animTform);
            }

            if (bone.parentIdx == -1)
                continue;

            Bone parent = bones.get(bone.parentIdx);
            // MATH ALERT:
            // bone.transform = parent.transform * bone.transform is
            // bone.transform = world/parent * parent/local-bone = model/local-bone
            // meaning the new transform maps from local bone coordinates to model coordinates.
            bone.transform = parent.transform.multiply(bone.transform);
        }
    }

    static private RigidTransform getTransformAtTime(
            ArrayList<Pair<Double, RigidTransform>> keyframe, double delta) {

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
