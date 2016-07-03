#define AMBIENT 0.2

uniform vec3 uLightPos;    // camera-space light position
uniform sampler2D uTexture;

varying mediump vec3 vPos;      // camera-space surface position
varying mediump vec3 vNormal;
varying mediump vec2 vTexCoord;

void main() {
    vec3 lightDir = normalize(uLightPos - vPos);
    float diffuse = min(max(dot(vNormal, lightDir) + AMBIENT, AMBIENT), 1.0);
    gl_FragColor = diffuse * texture2D(uTexture, vTexCoord);
}
