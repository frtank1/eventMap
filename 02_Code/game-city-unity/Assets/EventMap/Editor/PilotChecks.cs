using System;
using System.Collections.Generic;
using System.IO;
using UnityEngine;
using UnityEditor;
using EventMap.City;

namespace EventMap.CityEditor
{
    public static class PilotChecks
    {
        [Serializable] class Report { public string utc; public int passed; public string[] checks; }
        static readonly List<string> checks = new List<string>();
        static void Check(bool condition, string name) { if (!condition) throw new Exception("FAILED: " + name); checks.Add(name); }
        [MenuItem("EventMap/Run pilot checks")]
        public static void Run()
        {
            checks.Clear();
            Check(CityGeo.Project(CityGeo.Latitude, CityGeo.Longitude) == Vector3.zero, "Geo origin at Baiterek");
            var test = CityGeo.Unproject(CityGeo.Project(51.10806, 71.4025));
            Check(Math.Abs(test.lat - 51.10806) < .00000001 && Math.Abs(test.lng - 71.4025) < .00000001, "Geo round trip arena within millimetres");
            Check(CityGeo.Project(51.13, 71.44).x > 0 && CityGeo.Project(51.13, 71.44).z > 0, "East / north axes");
            Check(!CityGeo.InBounds(double.NaN, 71.4) && !CityGeo.InBounds(51.1, double.PositiveInfinity), "Reject invalid coordinates");
            Check(!CityGeo.InBounds(40, 30), "Reject outside Astana");
            var appearance = new AvatarAppearance { skin = 3, outfit = 4, hairstyle = 2, backpack = false };
            Check(AvatarAppearance.TryCode(appearance.ToCode(), out var parsed) && parsed.skin == 3 && parsed.outfit == 4 && parsed.hairstyle == 2 && !parsed.backpack, "Appearance round trip");
            Check(!AvatarAppearance.TryCode("https://example.com", out _), "No URL imports");
            Check(!AvatarAppearance.TryCode("EMAV1:%%%%", out _), "Reject corrupt code");
            Check(!AvatarAppearance.TryCode(new string('x', 1025), out _), "Bounded appearance payload");
            Check(!AvatarAppearance.TryCode(new AvatarAppearance { outfit = 99 }.ToCode(), out _), "Reject invalid appearance palette");
            Check(!AvatarAppearance.TryCode(new AvatarAppearance { version = 2 }.ToCode(), out _), "Reject incompatible appearance version");
            var sample = new CityMesh { positions = new float[] { 0, 0, 1, 1, 0, 1, 0, 1, 1 }, normals = new float[] { 0, 0, 1, 0, 0, 1, 0, 0, 1 }, indices = new[] { 0, 1, 2 } };
            var mesh = PilotProject.ConvertMesh(sample);
            Check(mesh.vertices[0].z == -1 && mesh.triangles[1] == 2 && mesh.normals[0].z == -1, "Three.js handedness conversion");
            Check(Vector3.Dot(Vector3.Cross(mesh.vertices[mesh.triangles[1]] - mesh.vertices[0], mesh.vertices[mesh.triangles[2]] - mesh.vertices[0]), mesh.normals[0]) > 0, "Normals agree with reversed winding");
            UnityEngine.Object.DestroyImmediate(mesh);
            foreach (var id in new[] { "baiterek", "akorda", "arena" })
            {
                var prefab = AssetDatabase.LoadAssetAtPath<GameObject>("Assets/EventMap/Imported/" + id + "/" + id + ".prefab");
                Check(prefab && prefab.GetComponent<CityPlace>().placeId == id && prefab.GetComponentsInChildren<MeshFilter>().Length > 0, "Imported landmark " + id);
            }
            Check(File.Exists(PilotProject.ScenePath), "Scene ready to run");
            Directory.CreateDirectory("Reports");
            File.WriteAllText("Reports/pilot-checks.json", JsonUtility.ToJson(new Report { utc = DateTime.UtcNow.ToString("O"), passed = checks.Count, checks = checks.ToArray() }, true));
            Debug.Log("EVENTMAP: " + checks.Count + " checks passed");
        }
    }
}
