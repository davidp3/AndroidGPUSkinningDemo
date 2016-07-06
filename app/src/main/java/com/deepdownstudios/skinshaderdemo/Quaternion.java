package com.deepdownstudios.skinshaderdemo;

import com.deepdownstudios.util.Util;

public class Quaternion {
    public double[] values = new double[4];           // w, x, y, z

    public Quaternion(Quaternion o) {
        set(o);
    }

    public Quaternion(double w, double x, double y, double z) { set(w, x, y, z); }

    public Quaternion(double angle, double[] axis) {
        double ccos = Math.cos(angle / 2.0);
        double csin = Math.sin(angle / 2.0);
        double c = Math.sqrt(axis[0]*axis[0] + axis[1]*axis[1] + axis[2]*axis[2]);
        Util.Assert (c != 0.0);
        c = csin / c;
        set(ccos, c * axis[0], c * axis[1], c * axis[2]);
        normalize();
    }

    /**
     * Create Quaternion that is equivalent to the given Euler rotation.
     * This definition of Euler angles (I think it corresponds to XZY order)
     * was determined with the Blender Python MS3D exporter:
     * https://gitlab.tul.cz/jiri.hnidek/blender/raw/1e3bdcf19846974b11c5a497eea56a34cdec0dde/release/scripts/ms3d_import.py
     *
     * @param eulerX Rotation about X axis (yaw)
     * @param eulerY Rotation about Y axis (pitch)
     * @param eulerZ Rotation about Z axis (roll)
     */
    public Quaternion(double eulerX, double eulerY, double eulerZ) {
        double c1 = Math.cos(eulerX/2.0);
        double s1 = Math.sin(eulerX/2.0);
        double c2 = Math.cos(eulerY/2.0);
        double s2 = Math.sin(eulerY/2.0);
        double c3 = Math.cos(eulerZ/2.0);
        double s3 = Math.sin(eulerZ/2.0);
        double c1c2 = c1*c2;
        double s1s2 = s1*s2;
        values[0] = c1c2*c3 + s1s2*s3;
        values[1] = s1*c2*c3 - c1*s2*s3;
        values[2] = c1*s2*c3 + s1*c2*s3;
        values[3] = c1c2*s3 - s1s2*c3;
    }

    public void set(Quaternion o) {
        values[0] = o.values[0];    values[1] = o.values[1];
        values[2] = o.values[2];    values[3] = o.values[3];
    }

    public void set(double w, double x, double y, double z) {
        values[0] = w;    values[1] = x;    values[2] = y;    values[3] = z;
    }

    // Right-multiply quaternion.  Computes this * quat.
    public Quaternion multiply(Quaternion quat) {
        double dot = values[1]*quat.values[1] + values[2]*quat.values[2] + values[3]*quat.values[3];
        return new Quaternion(
            values[0]*quat.values[0]-dot,
            values[0]*quat.values[1] + quat.values[0]*values[1] + values[2]*quat.values[3] - values[3]*quat.values[2],
            values[0]*quat.values[2] + quat.values[0]*values[2] + values[3]*quat.values[1] - values[1]*quat.values[3],
            values[0]*quat.values[3] + quat.values[0]*values[3] + values[1]*quat.values[2] - values[2]*quat.values[1]);
    }

    /**
     * Convert to a 3-D axis and an angle.
     * @param result    (axis-x, axis-y, axis-z, angle)
     */
    @SuppressWarnings("unused")
    public void toAxisAngle(double[] result) {
        Util.Assert (values[0] <= 1);        // otherwise its definitely not normalized
        double len = Math.sqrt(1.0 - values[0] * values[0]);
        if (len == 0.0) {
            // We equate a 360 degree rotation with a 0 degree one and represent it as
            // a 0 degree rotation about the x axis.
            result[0] = 1.0;
            result[1] = 0.0;
            result[2] = 0.0;
            result[3] = 0.0;
        } else {
            result[0] = values[1] / len;
            result[1] = values[2] / len;
            result[2] = values[3] / len;
            result[3] = 2.0 * Math.acos(values[0]);
        }
    }

    public Quaternion normalize() {
        double s = values[0]*values[0] + values[1]*values[1] + values[2]*values[2] + values[3]*values[3];
        s = Math.sqrt(s);
        values[0] /= s;    values[1] /= s;    values[2] /= s;    values[3] /= s;
        return this;
    }

    /**
     * Rotate pos by this (assumed unit) quaternion.
     * @param pos   The 3-D point to rotate.
     */
    public double[] transformPoint(double[] pos) {
        Quaternion posQuat = new Quaternion(0.0, pos[0], pos[1], pos[2]);
        Quaternion result = multiply(posQuat).multiply(conjugate());
        double[] ret = new double[3];
        ret[0] = result.values[1];    ret[1] = result.values[2];    ret[2] = result.values[3];
        return ret;
    }

    private Quaternion conjugate() {
        return new Quaternion(values[0], -values[1], -values[2], -values[3]);
    }

    public float[] asMatrix() {
        float[] ret = new float[16];        // filled with zeros
        ret[15] = 1.0f;

        // Diagonal
        double w2 = values[0]*values[0];
        double x2 = values[1]*values[1];
        double y2 = values[2]*values[2];
        double z2 = values[3]*values[3];
        double len2 = w2+x2+y2+z2;
        ret[0]  = (float)(( x2 - y2 - z2 + w2)/len2);
        ret[5]  = (float)((-x2 + y2 - z2 + w2)/len2);
        ret[10] = (float)((-x2 - y2 + z2 + w2)/len2);

        // Off-diagonal
        double wx = 2.0*values[0]*values[1];
        double wy = 2.0*values[0]*values[2];
        double wz = 2.0*values[0]*values[3];
        double xy = 2.0*values[1]*values[2];
        double xz = 2.0*values[1]*values[3];
        double yz = 2.0*values[2]*values[3];
        ret[1] = (float)((xy + wz)/len2);
        ret[4] = (float)((xy - wz)/len2);
        ret[2] = (float)((xz - wy)/len2);
        ret[8] = (float)((xz + wy)/len2);
        ret[6] = (float)((yz + wx)/len2);
        ret[9] = (float)((yz - wx)/len2);

        return ret;
    }

    /**
     * Spherically interpolate from this (when weight == 0) to o (when weight == 1.0)
     * @param o         The quaternion corresponding to weight 1.0
     * @param weight    The weight of the interpolation.  0 <= weight <= 1.0
     * @return          The interpolated quaternion.
     */
    public Quaternion slerp(Quaternion o, double weight) {
        double cosHalf = dot(o);
        // Check for zero angle between quats (implying no need to interpolate)
        if (Math.abs(cosHalf) >= 1.0) {
            return new Quaternion(this);
        }

        double halfAngle = Math.acos(cosHalf);
        double sinHalf = Math.sqrt(1.0 - cosHalf * cosHalf);
        // Arbitrary choice for 180 degree rotation, as the axis is ill-defined
        // (any will do equally "well" geodesically -- all options are the same length
        // so all are the shortest path).
        if (Math.abs(sinHalf) < 1e-5) {
            return new Quaternion(
                    values[0] * 0.5 + o.values[0] * 0.5, values[1] * 0.5 + o.values[1] * 0.5,
                    values[2] * 0.5 + o.values[2] * 0.5, values[3] * 0.5 + o.values[3] * 0.5);
        }
        double c  = Math.sin((1 - weight) * halfAngle) / sinHalf;
        double oc = Math.sin(weight * halfAngle) / sinHalf;

        return new Quaternion(
                c * values[0] + oc * o.values[0], c * values[1] + oc * o.values[1],
                c * values[2] + oc * o.values[2], c * values[3] + oc * o.values[3]);
    }

    private double dot(Quaternion o) {
        double sum = 0;
        for (int i=0; i<4; i++)
            sum += values[i]*o.values[i];
        return sum;
    }
}
