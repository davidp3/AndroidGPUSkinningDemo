package com.deepdownstudios.skinshaderdemo;

import static com.deepdownstudios.skinshaderdemo.BasicModel.RigidTransform;

/**
 * Dual quaternions encode a rotation and a translation in an 8-vector.
 * See https://www.cs.utah.edu/~ladislav/kavan07skinning/kavan07skinning.pdf
 */
public class DualQuaternion {
    public DualQuaternion(RigidTransform transform) {
        Quaternion dualPart =
                new Quaternion(0.0, 0.5*transform.pos[0], 0.5*transform.pos[1], 0.5*transform.pos[2])
                        .multiply(transform.quat);
        System.arraycopy(transform.quat.values, 0, values, 0, 4);       // "real" part of dual quat
        System.arraycopy(dualPart.values, 0, values, 4, 4);             // "dual" part of dual quat
    }

    public double[] values = new double[8];
}
