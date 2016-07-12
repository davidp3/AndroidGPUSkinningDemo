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
// These are (qw,qx,qy,qz),(qw',qx',qy',qz'),... (one pair of vec4s per joint)
// These 8 coordinates for the real and dual parts of the dual quaternion, respectively.
uniform highp vec4 uBoneTforms[N_BONE_TFORMS*2];

// view-space
varying mediump vec3 vPos;

// view-space
varying mediump vec3 vNormal;
varying mediump vec2 vTexCoord;

mat4 dualQuatToMat(vec4 realPart, vec4 dualPart) {
    float x0 = realPart.x, y0 = realPart.y, z0 = realPart.z, w0 = realPart.w;
    float xe = dualPart.x, ye = dualPart.y, ze = dualPart.z, we = dualPart.w;

    float t0 = 2.0 * (-we*x0 + xe*w0 - ye*z0 + ze*y0);
    float t1 = 2.0 * (-we*y0 + xe*z0 + ye*w0 - ze*x0);
    float t2 = 2.0 * (-we*z0 - xe*y0 + ye*x0 + ze*w0);

    return mat4(1.0 - 2.0*y0*y0 - 2.0*z0*z0, 2.0*x0*y0 + 2.0*w0*z0, 2.0*x0*z0 - 2.0*w0*y0, 0,
        2.0*x0*y0 - 2.0*w0*z0, 1.0 - 2.0*x0*x0 - 2.0*z0*z0, 2.0*y0*z0 + 2.0*w0*x0, 0,
        2.0*x0*z0 + 2.0*w0*y0, 2.0*y0*z0 - 2.0*w0*x0, 1.0 - 2.0*x0*x0 - 2.0*y0*y0, 0,
        t0, t1, t2, 1.0);
}

void main() {
    mediump ivec2 iBoneIndices2 = ivec2(aBoneIndices);
    mediump ivec2 rem2 = iBoneIndices2/32;
    mediump ivec4 iBoneIndices = ivec4(iBoneIndices2.x - rem2.x*32, rem2.x, iBoneIndices2.y - rem2.y*32, rem2.y);
    mediump vec4 boneWeights = aBoneWeights;

    // Calculate the weighted dual quaternion sum.
    // See Sec 3.4 of https://www.cs.utah.edu/~ladislav/kavan07skinning/kavan07skinning.pdf
    // (This is the "optimized" form.)
    highp vec4 realPart = boneWeights.x * uBoneTforms[iBoneIndices.x*2];
    highp vec4 dualPart = boneWeights.x * uBoneTforms[iBoneIndices.x*2+1];
    for (int i=0; i<3; i++) {
        iBoneIndices = iBoneIndices.yzwx;
        boneWeights = boneWeights.yzwx;
        if (boneWeights.x != 0.0) {
            realPart = realPart + boneWeights.x * uBoneTforms[iBoneIndices.x*2];
            dualPart = dualPart + boneWeights.x * uBoneTforms[iBoneIndices.x*2+1];
        }
    }
    float realLength = length(realPart);
    realPart = realPart/realLength;
    dualPart = dualPart/realLength;
    highp mat4 quatMat = dualQuatToMat(realPart, dualPart);

    // modelPos = (unitless) * model-space = model-space
    highp vec4 modelPos = quatMat * vec4(aPosition, 1.0);

    // gl_Position = projected-space/model-space * model-space = projected-space
    gl_Position = uMVPMatrix * modelPos;

    // vPos = view-space/model-space * model-space = view-space
    vPos = vec3(uMVMatrix * modelPos);

    // Same
    vNormal = normalize(vec3(uVTIMMatrix * quatMat * vec4(aNormal, 0)));

    vTexCoord = aTexCoord;
}
