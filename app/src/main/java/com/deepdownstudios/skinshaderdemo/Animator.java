package com.deepdownstudios.skinshaderdemo;

import com.deepdownstudios.util.Util;

import static com.deepdownstudios.skinshaderdemo.BasicModel.*;

/**
 * Calculates bone positions at a given time for a given skeleton/animation.
 * This is currently a simple enum for the shader types but this could be
 * extended to include animation blending, etc.
 */
public enum Animator {
    NORMAL {
        @Override
        public String toString() {
            return "matrix";
        }
    },
    QUAT {
        @Override
        public String toString() {
            return "quaternion";
        }
    },
    DUAL_QUAT {
        @Override
        public String toString() {
            return "dual quat";
        }
    };

    public Bones getBonesAtTime(Animation animation,
                                Skeleton skeleton, double delta) {
        switch (this) {
            case NORMAL:
                return new MatrixBones(animation, skeleton, delta);
            case QUAT:
                return new QuatBones(animation, skeleton, delta);
        }
        Util.Assert(this.equals(DUAL_QUAT));
        return new DualQuatBones(animation, skeleton, delta);
    }
}
