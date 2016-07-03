#define N_BONE_TFORMS 18

// model-space
attribute highp vec3 aPosition;
// model-space
attribute mediump vec3 aNormal;
attribute mediump vec2 aTexCoord;
// TODO: PERFORMANCE: Pack into one float.
attribute mediump vec4 aBoneIndices;
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
uniform highp mat4 uBoneTforms[N_BONE_TFORMS];

// view-space
varying mediump vec3 vPos;

// view-space
varying mediump vec3 vNormal;
varying mediump vec2 vTexCoord;

void main() {
    mediump ivec4 iBoneIndices = ivec4(aBoneIndices);
    mediump vec4 boneWeights = aBoneWeights;

    // Write the weighted matrix sum to avoid extra calculations for 0-weight
    // bones, which are very common.  TODO: Profile that.
    // Note that the weights are unitless, so the sum has the same units as the
    // individual transforms, which is none (they were unitless).  So we are
    // mapping from model-space back into model-space, which is what we want.
    highp mat4 boneMatWeightSum = boneWeights.x * uBoneTforms[iBoneIndices.x];
    for (int i=0; i<3; i++) {
        iBoneIndices = iBoneIndices.yzwx;
        boneWeights = boneWeights.yzwx;
        if (boneWeights.x != 0.0) {
            boneMatWeightSum = boneMatWeightSum + boneWeights.x * uBoneTforms[iBoneIndices.x];
        }
    }

    // modelPos = (unitless) * model-space = model-space
    highp vec4 modelPos = boneMatWeightSum * vec4(aPosition, 1.0);

    // gl_Position = projected-space/model-space * model-space = projected-space
    gl_Position = uMVPMatrix * modelPos;

    // vPos = view-space/model-space * model-space = view-space
    vPos = vec3(uMVMatrix * modelPos);

    // Same
    vNormal = normalize(vec3(uVTIMMatrix * (boneMatWeightSum * vec4(aNormal, 0.0))));

    vTexCoord = aTexCoord;
}
