#define N_BONE_TFORMS 32

// model-space
attribute highp vec3 aPosition;
// model-space
attribute mediump vec3 aNormal;
attribute mediump vec2 aTexCoord;
// Each coordinate of aBoneIndices has 2 bones, each packed into 5 bits.
// mediump guarantees 10 bits of precision.
attribute mediump vec2 aBoneIndices;
attribute mediump vec4 aBoneWeights;

// uMVPMatrix = projection-space/view-space * view-space/world-space * world-space/model-space
//    = projection-space/model-space
uniform highp mat4 uMVPMatrix;

// uMVMatrix = view-space/world-space * world-space/model-space
//    = view-space/model-space (for lighting)
uniform highp mat4 uMVMatrix;

// Similar.  Indeed, without scaling, this is the same as uMVMatrix.
uniform mediump mat4 uVTIMMatrix;

// uBoneTforms = unitless transformations
// These are (qx,qy,qz),(x,y,z),... (one pair of vec3s per joint)
// (qx,qy,qz,sgn(qw)*sqrt(1-qx*qx-qy*qy-qz*qz)) represents the bone rotation quaternion.
// (We are assured by the Java program that sgn(qw) is always 1.)
// (x,y,z) represent bone translation.
uniform highp vec3 uBoneTforms[N_BONE_TFORMS*2];

// view-space
varying mediump vec3 vPos;

// view-space
varying mediump vec3 vNormal;
varying mediump vec2 vTexCoord;

// quat1 * quat2
vec4 quatmult(vec4 quat1, vec4 quat2) {
    return vec4(
        quat1.w*quat2.x + quat1.x*quat2.w + quat1.y*quat2.z - quat1.z*quat2.y,      // x
        quat1.w*quat2.y + quat1.y*quat2.w + quat1.z*quat2.x - quat1.x*quat2.z,      // y
        quat1.w*quat2.z + quat1.z*quat2.w + quat1.x*quat2.y - quat1.y*quat2.x,      // z
        quat1.w*quat2.w - quat1.x*quat2.x - quat1.y*quat2.y - quat1.z*quat2.z);     // w
}

vec4 quatconj(vec4 quat) {
    return vec4(-quat.x, -quat.y, -quat.z, quat.w);
}

vec3 quatTransform(vec4 quat, vec3 point) {
    vec4 quatPoint = vec4(point, 0.0);
    return quatmult(quatmult(quat, quatPoint), quatconj(quat)).xyz;
}

void main() {
    mediump ivec2 iBoneIndices2 = ivec2(aBoneIndices);
    mediump ivec2 rem2 = iBoneIndices2/32;
    mediump ivec4 iBoneIndices = ivec4(iBoneIndices2.x - rem2.x*32, rem2.x, iBoneIndices2.y - rem2.y*32, rem2.y);
    mediump vec4 boneWeights = aBoneWeights;

    // Calculate the weighted quaternion and translation sums.
    // Note that the weights are unitless, so the sum has the same units as the
    // individual transforms, which is none (they were unitless).  So we are
    // mapping from model-space back into model-space, which is what we want.
    highp vec4 boneQuat = boneWeights.x *
                vec4(uBoneTforms[iBoneIndices.x*2], sqrt(1.0-dot(uBoneTforms[iBoneIndices.x*2],uBoneTforms[iBoneIndices.x*2])));
    highp vec3 boneTrans = boneWeights.x * uBoneTforms[iBoneIndices.x*2+1];
    for (int i=0; i<3; i++) {
        iBoneIndices = iBoneIndices.yzwx;
        boneWeights = boneWeights.yzwx;
        if (boneWeights.x != 0.0) {
            boneQuat = boneQuat + boneWeights.x * vec4(uBoneTforms[iBoneIndices.x*2],
                            sqrt(1.0-dot(uBoneTforms[iBoneIndices.x*2],uBoneTforms[iBoneIndices.x*2])));
            boneTrans = boneTrans + boneWeights.x * uBoneTforms[iBoneIndices.x*2+1];
        }
    }
    boneQuat = normalize(boneQuat);

    // modelPos = (unitless) * model-space = model-space
    highp vec4 modelPos = vec4(quatTransform(boneQuat, aPosition) + boneTrans, 1.0);

    // gl_Position = projected-space/model-space * model-space = projected-space
    gl_Position = uMVPMatrix * modelPos;

    // vPos = view-space/model-space * model-space = view-space
    vPos = vec3(uMVMatrix * modelPos);

    // Same
    vNormal = normalize(vec3(uVTIMMatrix * vec4(quatTransform(boneQuat, aNormal), 0)));

    vTexCoord = aTexCoord;
}
