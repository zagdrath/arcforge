#version 330
#extension GL_ARB_separate_shader_objects : require

// Vanilla's core/entity vertex shader for the large models' moving parts (rotors, fans, doors), each built once and kept
// on the GPU (client/renderer/StaticMeshes.java, submitMoving): their vertices and normals are in the part's rest place,
// TextureMat (unused by these pipelines otherwise) turns them to where the part is now, and ModelOffset moves them to
// where the pose stack would have put them (camera-relative). Each face is then shaded as a block face turned its way is,
// from its turned normal, as MeshRendering.emit shades a part built in place, so a turned part looks as one rebuilt at
// its angle each frame did; the rest is the entity shader with an upward normal, as for the still parts. With
// ENTITY_LIT, a part the entity shader lit from its own normals (the Gas Turbine Array's rotor) is lit so, from its
// turned normal, with no face shading of its own.

#if defined(PER_FACE_LIGHTING) || !defined(NO_CARDINAL_LIGHTING)
#include <minecraft:light.glsl>
#endif
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
layout(location = 5) in vec3 Normal;

#if !defined(NO_OVERLAY) && !defined(OIT_ALPHA_ONLY)
uniform sampler2D Sampler1;
#endif

#if !defined(EMISSIVE) && !defined(OIT_ALPHA_ONLY)
uniform sampler2D Sampler2;
#endif

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;

#ifdef PER_FACE_LIGHTING
layout(location = 2) out vec4 vertexPerFaceColorBack;
layout(location = 3) out vec4 vertexPerFaceColorFront;
#else
layout(location = 2) out vec4 vertexColor;
#endif

#ifndef EMISSIVE
layout(location = 4) out vec4 lightMapColor;
#endif

#ifndef NO_OVERLAY
layout(location = 5) out vec4 overlayColor;
#endif

layout(location = 6) out vec2 texCoord0;

void main() {
    vec3 pos = (TextureMat * vec4(Position, 1.0)).xyz + ModelOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);

    // The normal turned with the part (by the inverse transpose, as a pose's normal matrix is, for a part also scaled).
    vec3 n = normalize(transpose(inverse(mat3(TextureMat))) * Normal);
#ifdef ENTITY_LIT
    vec4 shaded = Color;
    vec3 up = n;
#else
    // Block face shading: 0.6 facing along x, 0.8 along z, 1 up, 0.5 down.
    float shade = n.x * n.x * 0.6 + n.y * n.y * (n.y > 0.0 ? 1.0 : 0.5) + n.z * n.z * 0.8;
    vec4 shaded = vec4(Color.rgb * shade, Color.a);
    vec3 up = vec3(0.0, 1.0, 0.0);
#endif

#ifdef PER_FACE_LIGHTING
    vec2 light = minecraft_compute_light(Light0_Direction, Light1_Direction, up);
    vertexPerFaceColorBack = minecraft_mix_light_separate(-light, shaded);
    vertexPerFaceColorFront = minecraft_mix_light_separate(light, shaded);
#elif defined(NO_CARDINAL_LIGHTING)
    vertexColor = shaded;
#else
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, up, shaded);
#endif

#if !defined(EMISSIVE) && !defined(OIT_ALPHA_ONLY)
    lightMapColor = sample_lightmap(Sampler2, UV2);
#endif

#if !defined(NO_OVERLAY) && !defined(OIT_ALPHA_ONLY)
    overlayColor = texelFetch(Sampler1, UV1, 0);
#endif

    texCoord0 = UV0;
}
