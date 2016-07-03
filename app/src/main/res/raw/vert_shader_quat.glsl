#define N_BONE_TFORMS 18

attribute highp vec3 aPosition;
attribute mediump vec2 aTexCoord;
attribute mediump vec3 aNormal;
// TODO: Pack into one float.
attribute mediump vec4 aBoneIndices;
attribute mediump vec4 aBoneWeights;

uniform highp mat4 uMVPMatrix;      // model * view * projection
uniform highp mat4 uMVMatrix;       // model * view (for lighting)
uniform mediump mat4 uVTIMMatrix;
uniform highp mat4 uBoneTforms[N_BONE_TFORMS];

varying mediump vec3 vPos;
varying mediump vec2 vTexCoord;
varying mediump vec3 vNormal;

void main() {
    // convert 16-bit float bone indices to 16-bit ints
    mediump ivec4 iBoneIndices = ivec4(aBoneIndices);
    mediump vec4 boneWeights = aBoneWeights;

    // Write the weighted matrix sum to avoid extra calculations for 0-weight
    // bones, which are very common.  TODO: Profile that.
    highp mat4 boneMatWeightSum = boneWeights.x * uBoneTforms[iBoneIndices.x];
    for (int i=0; i<3; i++) {
        iBoneIndices = iBoneIndices.yzwx;
        boneWeights = boneWeights.yzwx;
        if (boneWeights.x != 0.0) {
            boneMatWeightSum = boneMatWeightSum + boneWeights.x * uBoneTforms[iBoneIndices.x];
        }
    }

    highp vec4 modelPos = boneMatWeightSum * vec4(aPosition, 1.0);
    gl_Position = uMVPMatrix * modelPos;
    vPos = vec3(uMVMatrix * modelPos);
    vNormal = normalize(vec3(uVTIMMatrix * (boneMatWeightSum * vec4(aNormal, 0.0))));
    vTexCoord = aTexCoord;
}
