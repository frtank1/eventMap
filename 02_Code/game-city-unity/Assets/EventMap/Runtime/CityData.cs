using System;
using System.Text;
using UnityEngine;

namespace EventMap.City
{
    // Local tangent plane, metres; Unity +Z = north. Keep doubles until subtracting origin.
    public static class CityGeo
    {
        public const double Latitude = 51.12822, Longitude = 71.43056;
        const double Metres = 111320;
        static readonly double LonMetres = Metres * Math.Cos(Latitude * Math.PI / 180);
        public static Vector3 Project(double lat, double lng) =>
            new Vector3((float)((lng - Longitude) * LonMetres), 0, (float)((lat - Latitude) * Metres));
        public static (double lat, double lng) Unproject(Vector3 p) =>
            (Latitude + p.z / Metres, Longitude + p.x / LonMetres);
        public static bool InBounds(double lat, double lng) =>
            !double.IsNaN(lat) && !double.IsNaN(lng) && lat >= 50.9 && lat <= 51.4 && lng >= 71.1 && lng <= 71.9;
    }

    [Serializable] public class CityManifest { public int schemaVersion, engineVersion; public string release; public CityEntry[] models; }
    [Serializable] public class CityEntry { public string id, title, file, sha256; public double lat, lng; public float height, radius; public int bytes; }
    [Serializable] public class CityModel { public int schemaVersion; public string id; public CityMaterial[] materials; public CityMesh[] meshes; }
    [Serializable] public class CityMaterial { public int color, emissive, side; public float roughness, metalness, opacity, emissiveIntensity; public bool transparent; }
    [Serializable] public class CityMesh { public float[] positions, normals; public int[] indices; public int material; public bool detail; }

    [Serializable]
    public class AvatarAppearance
    {
        public int version = 1;
        public int skin = 1, hair = 0, outfit = 0, hairstyle = 0;
        public bool backpack = true;
        public static readonly Color[] Skin = { Hex(0xf5d2b3), Hex(0xdca67e), Hex(0xb87953), Hex(0x734b37) };
        public static readonly Color[] Hair = { Hex(0x25252e), Hex(0x634332), Hex(0xc39458), Hex(0xd5d5e1) };
        public static readonly Color[] Outfit = { Hex(0xa17cf5), Hex(0x53c6b8), Hex(0xf2ae56), Hex(0x689feb), Hex(0xe5809c) };
        public bool Valid => version == 1 && skin >= 0 && skin < Skin.Length && hair >= 0 && hair < Hair.Length &&
                             outfit >= 0 && outfit < Outfit.Length && hairstyle >= 0 && hairstyle <= 2;
        public static Color Hex(int n) => new Color(((n >> 16) & 255) / 255f, ((n >> 8) & 255) / 255f, (n & 255) / 255f);
        // Appearance only. No account IDs, coordinates, permissions, URLs or executable content.
        public string ToCode() => "EMAV1:" + Convert.ToBase64String(Encoding.UTF8.GetBytes(JsonUtility.ToJson(this)));
        public static bool TryCode(string code, out AvatarAppearance value)
        {
            value = null;
            if (code == null || code.Length > 1024 || !code.StartsWith("EMAV1:", StringComparison.Ordinal)) return false;
            try
            {
                var parsed = JsonUtility.FromJson<AvatarAppearance>(Encoding.UTF8.GetString(Convert.FromBase64String(code.Substring(6))));
                if (parsed == null || !parsed.Valid) return false;
                value = parsed;
                return true;
            }
            catch (Exception) { return false; }
        }
        public static AvatarAppearance Load()
        {
            return TryCode(PlayerPrefs.GetString("eventmap.pilot.avatar", ""), out var saved) ? saved : new AvatarAppearance();
        }
        public void Save() { PlayerPrefs.SetString("eventmap.pilot.avatar", ToCode()); PlayerPrefs.Save(); }
    }
}
