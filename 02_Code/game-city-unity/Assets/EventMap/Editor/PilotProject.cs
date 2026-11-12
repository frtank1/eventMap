using System;
using System.IO;
using System.Linq;
using System.Security.Cryptography;
using UnityEditor;
using UnityEditor.Build.Reporting;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.Rendering;
using EventMap.City;

namespace EventMap.CityEditor
{
    public static class PilotProject
    {
        const string Generated = "Assets/EventMap/Imported";
        public const string ScenePath = "Assets/EventMap/Scenes/CityPilot.unity";
        static string Source => Path.GetFullPath(Path.Combine(Application.dataPath, "../../game-city/content"));
        static void Require(bool condition, string message) { if (!condition) throw new InvalidDataException(message); }

        [MenuItem("EventMap/1 · Import current city models")]
        public static void ImportModels()
        {
            var manifest = JsonUtility.FromJson<CityManifest>(File.ReadAllText(Path.Combine(Source, "manifest.json")));
            Require(manifest.schemaVersion == 1 && manifest.engineVersion == 1 && manifest.models.Length is >= 1 and <= 12, "Unsupported manifest");
            Directory.CreateDirectory(Generated); AssetDatabase.Refresh();
            foreach (var entry in manifest.models)
            {
                Require(System.Text.RegularExpressions.Regex.IsMatch(entry.id, "^[a-z0-9-]{1,40}$"), "Invalid ID");
                Require(entry.file == "assets/" + entry.sha256 + ".json" && System.Text.RegularExpressions.Regex.IsMatch(entry.sha256, "^[0-9a-f]{64}$"), "Invalid asset path");
                Require(CityGeo.InBounds(entry.lat, entry.lng), "Invalid location");
                string sourceFile = Path.Combine(Source, entry.file);
                Require(new FileInfo(sourceFile).Length == entry.bytes && entry.bytes <= 6000000, "Asset size mismatch");
                byte[] bytes = File.ReadAllBytes(sourceFile);
                using (var sha = SHA256.Create()) Require(BitConverter.ToString(sha.ComputeHash(bytes)).Replace("-", "").ToLowerInvariant() == entry.sha256, "SHA256 mismatch");
                var model = JsonUtility.FromJson<CityModel>(System.Text.Encoding.UTF8.GetString(bytes)); Validate(model, entry.id);
                string folder = Generated + "/" + entry.id; Directory.CreateDirectory(folder); AssetDatabase.Refresh();
                var materials = model.materials.Select((spec, index) => SaveMaterial(folder, index, spec)).ToArray();
                var root = new GameObject(entry.title);
                var place = root.AddComponent<CityPlace>(); place.placeId = entry.id; place.title = entry.title;
                place.latitude = entry.lat; place.longitude = entry.lng; place.viewDistance = Mathf.Max(260, entry.radius * 3.4f);
                for (int i = 0; i < model.meshes.Length; i++)
                {
                    var source = model.meshes[i]; var mesh = ConvertMesh(source); mesh.name = entry.id + "-" + i;
                    string assetPath = folder + "/mesh-" + i + ".asset";
                    SaveAsset(mesh, assetPath);
                    var part = new GameObject(source.detail ? "Facade details" : "Architecture"); part.transform.SetParent(root.transform, false);
                    part.AddComponent<MeshFilter>().sharedMesh = AssetDatabase.LoadAssetAtPath<Mesh>(assetPath);
                    part.AddComponent<MeshRenderer>().sharedMaterial = materials[source.material];
                }
                // Generated prefab only. User-edited replacement prefabs belong in Assets/EventMap/Custom.
                PrefabUtility.SaveAsPrefabAsset(root, folder + "/" + entry.id + ".prefab"); UnityEngine.Object.DestroyImmediate(root);
            }
            AssetDatabase.SaveAssets(); Debug.Log("EVENTMAP: imported " + manifest.models.Length + " verified original landmarks");
        }
        public static void Validate(CityModel m, string id)
        {
            Require(m != null && m.schemaVersion == 1 && m.id == id && m.materials != null && m.materials.Length is > 0 and <= 45 && m.meshes != null && m.meshes.Length is > 0 and <= 45, "Invalid model");
            int count = 0;
            foreach (var mesh in m.meshes)
            {
                Require(mesh.positions != null && mesh.normals != null && mesh.indices != null && mesh.positions.Length > 0 && mesh.positions.Length % 3 == 0 && mesh.positions.Length == mesh.normals.Length, "Invalid vectors");
                int vertices = mesh.positions.Length / 3; count += vertices;
                Require(count <= 250000 && mesh.indices.Length % 3 == 0 && mesh.indices.Length <= 1500000 && mesh.indices.All(i => i >= 0 && i < vertices), "Invalid indices");
                Require(mesh.positions.All(v => !float.IsNaN(v) && !float.IsInfinity(v) && Mathf.Abs(v) <= 10000) && mesh.normals.All(v => !float.IsNaN(v) && !float.IsInfinity(v) && Mathf.Abs(v) <= 1.01f), "Non-finite geometry");
                Require(mesh.material >= 0 && mesh.material < m.materials.Length, "Invalid material reference");
            }
            foreach (var material in m.materials)
                Require(material != null && material.color >= 0 && material.color <= 0xffffff && material.emissive >= 0 && material.emissive <= 0xffffff && material.opacity >= 0 && material.opacity <= 1 && material.roughness >= 0 && material.roughness <= 1 && material.metalness >= 0 && material.metalness <= 1 && material.emissiveIntensity >= 0 && material.emissiveIntensity <= 100 && (material.side == 0 || material.side == 2), "Invalid material");
        }
        public static Mesh ConvertMesh(CityMesh source)
        {
            var vertices = new Vector3[source.positions.Length / 3]; var normals = new Vector3[vertices.Length];
            for (int i = 0; i < vertices.Length; i++)
            {
                // Three.js +Z south -> Unity +Z north: reflect Z AND reverse winding.
                vertices[i] = new Vector3(source.positions[i * 3], source.positions[i * 3 + 1], -source.positions[i * 3 + 2]);
                normals[i] = new Vector3(source.normals[i * 3], source.normals[i * 3 + 1], -source.normals[i * 3 + 2]);
            }
            int[] triangles = (int[])source.indices.Clone();
            for (int i = 0; i < triangles.Length; i += 3) { int tmp = triangles[i + 1]; triangles[i + 1] = triangles[i + 2]; triangles[i + 2] = tmp; }
            var result = new Mesh { indexFormat = IndexFormat.UInt32, vertices = vertices, normals = normals, triangles = triangles };
            result.RecalculateBounds(); return result;
        }
        static Material SaveMaterial(string folder, int index, CityMaterial spec)
        {
            var material = new Material(Shader.Find("Standard"));
            var color = AvatarAppearance.Hex(spec.color); color.a = spec.opacity; material.color = color;
            material.SetFloat("_Glossiness", 1 - spec.roughness); material.SetFloat("_Metallic", spec.metalness);
            material.SetColor("_EmissionColor", AvatarAppearance.Hex(spec.emissive) * spec.emissiveIntensity);
            if (spec.emissiveIntensity > 0 && spec.emissive != 0) material.EnableKeyword("_EMISSION");
            // Standard has no configurable double-sided pass; duplicate back faces for these static assets is unnecessary for current exports.
            if (spec.transparent)
            {
                material.SetFloat("_Mode", 3); material.SetInt("_SrcBlend", (int)BlendMode.One); material.SetInt("_DstBlend", (int)BlendMode.OneMinusSrcAlpha);
                material.SetInt("_ZWrite", 0); material.EnableKeyword("_ALPHAPREMULTIPLY_ON"); material.renderQueue = 3000;
            }
            string path = folder + "/surface-" + index + ".mat"; SaveAsset(material, path);
            return AssetDatabase.LoadAssetAtPath<Material>(path);
        }
        static void SaveAsset(UnityEngine.Object value, string path)
        {
            var old = AssetDatabase.LoadAssetAtPath<UnityEngine.Object>(path);
            if (old) { EditorUtility.CopySerialized(value, old); EditorUtility.SetDirty(old); UnityEngine.Object.DestroyImmediate(value); }
            else AssetDatabase.CreateAsset(value, path);
        }

        [MenuItem("EventMap/2 · Create pilot scene (generated)")]
        public static void CreateScene()
        {
            if (!Application.isBatchMode && !EditorSceneManager.SaveCurrentModifiedScenesIfUserWantsTo()) return;
            if (!Application.isBatchMode && File.Exists(ScenePath) && !EditorUtility.DisplayDialog("Generated scene", "Recreate CityPilot? Save custom scene changes under a different name first.", "Recreate", "Cancel")) return;
            var scene = EditorSceneManager.NewScene(NewSceneSetup.EmptyScene, NewSceneMode.Single);
            Directory.CreateDirectory("Assets/EventMap/Scenes"); Directory.CreateDirectory("Assets/EventMap/Custom");
            var manifest = JsonUtility.FromJson<CityManifest>(File.ReadAllText(Path.Combine(Source, "manifest.json")));
            var city = new GameObject("CITY · 1 unit = 1 metre").transform;
            foreach (var p in manifest.models)
            {
                var prefab = AssetDatabase.LoadAssetAtPath<GameObject>(Generated + "/" + p.id + "/" + p.id + ".prefab");
                Require(prefab, "Import models first");
                var instance = (GameObject)PrefabUtility.InstantiatePrefab(prefab); instance.transform.SetParent(city);
                instance.transform.position = CityGeo.Project(p.lat, p.lng);
            }
            var environment = new GameObject("DEMO surroundings · NOT geographic survey").transform;
            Material ground = EnvironmentMaterial("ground", 0x718879), stone = EnvironmentMaterial("stone", 0xc5cbbf),
                path = EnvironmentMaterial("path", 0xe2dbc8), grass = EnvironmentMaterial("grass", 0x83a17d),
                leaf = EnvironmentMaterial("leaf", 0x477963), bark = EnvironmentMaterial("bark", 0x6c6255),
                asphalt = EnvironmentMaterial("asphalt", 0x495967), water = EnvironmentMaterial("water", 0x6aadb3);
            PilotAvatar.Part(environment, "Ground", PrimitiveType.Cube, new Vector3(0, -1.2f, 0), new Vector3(8500, 2, 8500), ground);
            PilotAvatar.Part(environment, "Promenade", PrimitiveType.Cube, new Vector3(0, -.04f, -70), new Vector3(125, .22f, 360), stone);
            PilotAvatar.Part(environment, "Walkway", PrimitiveType.Cube, new Vector3(0, .10f, -90), new Vector3(44, .08f, 260), path);
            PilotAvatar.Part(environment, "Demo road", PrimitiveType.Cube, new Vector3(0, -.02f, -198), new Vector3(230, .18f, 98), asphalt);
            PilotAvatar.Part(environment, "Road island", PrimitiveType.Cube, new Vector3(0, .12f, -198), new Vector3(152, .2f, 45), grass);
            for (int x = -90; x <= 90; x += 12) foreach (int z in new[] { -160, -235 })
                PilotAvatar.Part(environment, "Lane marker", PrimitiveType.Cube, new Vector3(x, .13f, z), new Vector3(5, .02f, .25f), path);
            foreach (int side in new[] { -1, 1 })
            {
                for (int i = 0; i < 7; i++)
                {
                    var p = new Vector3(side * 45, .14f, 30 - i * 27);
                    PilotAvatar.Part(environment, "Tree trunk", PrimitiveType.Cylinder, p + Vector3.up * 2, new Vector3(.65f, 2, .65f), bark);
                    PilotAvatar.Part(environment, "Tree crown", PrimitiveType.Sphere, p + Vector3.up * 5, new Vector3(7, 8, 7), leaf);
                    PilotAvatar.Part(environment, "Planter", PrimitiveType.Cylinder, p, new Vector3(8, .18f, 8), grass);
                    PilotAvatar.Part(environment, "Bench", PrimitiveType.Cube, p + new Vector3(-side * 7, .5f, 0), new Vector3(1, .3f, 3), bark);
                    var lamp = PilotAvatar.Part(environment, "Lamp", PrimitiveType.Cylinder, p + new Vector3(-side * 10, 2.8f, 8), new Vector3(.15f, 2.8f, .15f), asphalt);
                    var lightObj = new GameObject("Night light"); lightObj.tag = "Respawn"; lightObj.transform.SetParent(environment); lightObj.transform.position = lamp.position + Vector3.up * 2.7f;
                    var point = lightObj.AddComponent<Light>(); point.type = LightType.Point; point.range = 18; point.intensity = 3; point.color = new Color(1, .79f, .49f); point.shadows = LightShadows.None;
                }
                PilotAvatar.Part(environment, "Reflecting pool", PrimitiveType.Cube, new Vector3(side * 80, .02f, 10), new Vector3(18, .2f, 68), water);
            }
            // Ordinary buildings are explicitly demonstrational, not mapped addresses.
            for (int i = 0; i < 6; i++) foreach (int side in new[] { -1, 1 })
            {
                float h = 24 + (i * 13 % 38); var p = new Vector3(side * (145 + i % 2 * 28), h / 2, 160 - i * 58);
                PilotAvatar.Part(environment, "Demo city block", PrimitiveType.Cube, p, new Vector3(38, h, 36), stone);
                for (int row = 1; row < h / 4; row++) PilotAvatar.Part(environment, "Facade band", PrimitiveType.Cube, new Vector3(p.x, row * 4, p.z), new Vector3(38.2f, 1.8f, 36.2f), water);
            }
            foreach (var p in manifest.models.Where(p => p.id != "baiterek"))
                PilotAvatar.Part(environment, p.title + " plaza", PrimitiveType.Cube, CityGeo.Project(p.lat, p.lng) + Vector3.down * .15f, new Vector3(p.radius * 3.2f, .25f, p.radius * 3.2f), stone);
            var lightGo = new GameObject("Sun"); var sun = lightGo.AddComponent<Light>(); sun.type = LightType.Directional;
            sun.intensity = 1.15f; sun.shadows = LightShadows.Soft; lightGo.transform.rotation = Quaternion.Euler(48, -35, 0);
            RenderSettings.ambientMode = AmbientMode.Flat; QualitySettings.shadowDistance = 350;
            var cameraGo = new GameObject("Main Camera"); cameraGo.tag = "MainCamera"; var cam = cameraGo.AddComponent<Camera>();
            cam.clearFlags = CameraClearFlags.SolidColor; cameraGo.AddComponent<AudioListener>(); cameraGo.AddComponent<CityCamera>();
            new GameObject("EventMap pilot controller").AddComponent<CityPilot>();
            EditorSceneManager.SaveScene(scene, ScenePath);
            EditorBuildSettings.scenes = new[] { new EditorBuildSettingsScene(ScenePath, true) };
            PlayerSettings.companyName = "EventMap"; PlayerSettings.productName = "EventMap City Pilot";
            PlayerSettings.defaultScreenWidth = 1280; PlayerSettings.defaultScreenHeight = 800;
            PlayerSettings.fullScreenMode = FullScreenMode.Windowed; PlayerSettings.resizableWindow = true;
            PlayerSettings.runInBackground = false;
            AssetDatabase.SaveAssets(); Debug.Log("EVENTMAP: pilot scene saved");
        }
        static Material EnvironmentMaterial(string name, int hex)
        {
            Directory.CreateDirectory(Generated + "/environment"); AssetDatabase.Refresh();
            var mat = PilotAvatar.Surface(AvatarAppearance.Hex(hex)); string path = Generated + "/environment/" + name + ".mat";
            SaveAsset(mat, path); return AssetDatabase.LoadAssetAtPath<Material>(path);
        }
        public static void Prepare() { ImportModels(); CreateScene(); PilotChecks.Run(); }
        [MenuItem("EventMap/3 · Build macOS pilot")]
        public static void BuildMac()
        {
            Directory.CreateDirectory("Builds");
            var report = BuildPipeline.BuildPlayer(new BuildPlayerOptions { scenes = new[] { ScenePath }, locationPathName = "Builds/EventMap City Pilot.app", target = BuildTarget.StandaloneOSX, options = BuildOptions.Development });
            if (report.summary.result != BuildResult.Succeeded) throw new Exception("Build failed: " + report.summary.result);
            Debug.Log("EVENTMAP: macOS build succeeded");
        }
    }
}
