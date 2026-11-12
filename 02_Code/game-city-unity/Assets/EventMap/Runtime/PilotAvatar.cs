using UnityEngine;

namespace EventMap.City
{
    // Original temporary articulated character. Swap for a rigged prefab later.
    public class PilotAvatar : MonoBehaviour
    {
        Transform body, vehicle, armL, armR, legL, legR;
        Material cloth, skin, hair, dark, accent, white;
        GameObject hairLong, cap, pack;
        float phase;
        public bool Driving { get; private set; }
        public bool Moving { get; set; }

        public static Material Surface(Color color)
        {
            var mat = new Material(Shader.Find("Standard")) { color = color };
            mat.SetFloat("_Glossiness", .22f);
            return mat;
        }
        public static Transform Part(Transform parent, string name, PrimitiveType type, Vector3 p, Vector3 scale, Material mat)
        {
            var obj = GameObject.CreatePrimitive(type); obj.name = name;
            obj.transform.SetParent(parent, false); obj.transform.localPosition = p; obj.transform.localScale = scale;
            obj.GetComponent<Renderer>().sharedMaterial = mat;
            var collider = obj.GetComponent<Collider>();
            if (Application.isPlaying) Destroy(collider); else DestroyImmediate(collider);
            return obj.transform;
        }
        public void Build(AvatarAppearance look)
        {
            cloth = Surface(AvatarAppearance.Outfit[look.outfit]); skin = Surface(AvatarAppearance.Skin[look.skin]);
            hair = Surface(AvatarAppearance.Hair[look.hair]); dark = Surface(AvatarAppearance.Hex(0x29354a));
            accent = Surface(AvatarAppearance.Hex(0xb39af8)); white = Surface(AvatarAppearance.Hex(0xf3f3ee));
            body = new GameObject("Character · 1.85 metres").transform; body.SetParent(transform, false);
            Part(body, "Coat", PrimitiveType.Capsule, new Vector3(0, 1.05f, 0), new Vector3(.51f, .36f, .34f), cloth);
            Part(body, "Neck", PrimitiveType.Cylinder, new Vector3(0, 1.43f, 0), new Vector3(.14f, .08f, .14f), skin);
            Part(body, "Head", PrimitiveType.Sphere, new Vector3(0, 1.66f, 0), new Vector3(.41f, .45f, .39f), skin);
            Part(body, "Hair", PrimitiveType.Sphere, new Vector3(0, 1.80f, -.035f), new Vector3(.43f, .23f, .39f), hair);
            hairLong = Part(body, "Long hair", PrimitiveType.Capsule, new Vector3(0, 1.60f, -.13f), new Vector3(.44f, .27f, .22f), hair).gameObject;
            cap = Part(body, "Cap", PrimitiveType.Cylinder, new Vector3(0, 1.84f, .05f), new Vector3(.49f, .045f, .48f), cloth).gameObject;
            Part(body, "Nose", PrimitiveType.Sphere, new Vector3(0, 1.65f, .20f), Vector3.one * .08f, skin);
            foreach (float x in new[] { -.085f, .085f })
                Part(body, "Eye", PrimitiveType.Sphere, new Vector3(x, 1.71f, .185f), new Vector3(.035f, .045f, .027f), dark);
            armL = Limb("Left arm", -.31f, 1.33f, .47f, .14f, cloth);
            armR = Limb("Right arm", .31f, 1.33f, .47f, .14f, cloth);
            Part(armL, "Hand", PrimitiveType.Sphere, new Vector3(0, -.5f, 0), Vector3.one * .14f, skin);
            Part(armR, "Hand", PrimitiveType.Sphere, new Vector3(0, -.5f, 0), Vector3.one * .14f, skin);
            legL = Limb("Left leg", -.135f, .83f, .66f, .18f, dark);
            legR = Limb("Right leg", .135f, .83f, .66f, .18f, dark);
            Part(legL, "Shoe", PrimitiveType.Cube, new Vector3(0, -.72f, .06f), new Vector3(.20f, .14f, .33f), white);
            Part(legR, "Shoe", PrimitiveType.Cube, new Vector3(0, -.72f, .06f), new Vector3(.20f, .14f, .33f), white);
            pack = Part(body, "Backpack", PrimitiveType.Cube, new Vector3(0, 1.12f, -.23f), new Vector3(.34f, .45f, .22f), accent).gameObject;
            vehicle = new GameObject("Vehicle · demo").transform; vehicle.SetParent(transform, false);
            Part(vehicle, "Body", PrimitiveType.Cube, new Vector3(0, .65f, 0), new Vector3(1.7f, .6f, 3.3f), cloth);
            Part(vehicle, "Cabin", PrimitiveType.Cube, new Vector3(0, 1.16f, -.17f), new Vector3(1.45f, .65f, 1.85f), dark);
            Part(vehicle, "Roof", PrimitiveType.Cube, new Vector3(0, 1.52f, -.17f), new Vector3(1.51f, .1f, 1.90f), cloth);
            foreach (float x in new[] { -.86f, .86f }) foreach (float z in new[] { -1.05f, 1.05f })
                Part(vehicle, "Wheel", PrimitiveType.Cylinder, new Vector3(x, .37f, z), new Vector3(.62f, .13f, .62f), dark).localRotation = Quaternion.Euler(0, 0, 90);
            foreach (float x in new[] { -.57f, .57f })
                Part(vehicle, "Headlight", PrimitiveType.Cube, new Vector3(x, .73f, 1.66f), new Vector3(.32f, .18f, .025f), white);
            Apply(look); SetDriving(false);
        }
        Transform Limb(string name, float x, float y, float length, float width, Material material)
        {
            var pivot = new GameObject(name).transform; pivot.SetParent(body, false); pivot.localPosition = new Vector3(x, y, 0);
            Part(pivot, name, PrimitiveType.Capsule, new Vector3(0, -length / 2, 0), new Vector3(width, length / 2, width), material);
            return pivot;
        }
        public void Apply(AvatarAppearance look)
        {
            cloth.color = AvatarAppearance.Outfit[look.outfit]; skin.color = AvatarAppearance.Skin[look.skin]; hair.color = AvatarAppearance.Hair[look.hair];
            hairLong.SetActive(look.hairstyle == 1); cap.SetActive(look.hairstyle == 2); pack.SetActive(look.backpack);
        }
        public void SetDriving(bool driving) { Driving = driving; if (body) { body.gameObject.SetActive(!driving); vehicle.gameObject.SetActive(driving); } }
        public void SetDetail(bool visible) { if (body) { body.gameObject.SetActive(visible && !Driving); vehicle.gameObject.SetActive(visible && Driving); } }
        void Update()
        {
            if (!body) return;
            if (Moving) phase += Time.deltaTime * 7;
            float swing = Moving ? Mathf.Sin(phase) * 26 : 0;
            armL.localRotation = Quaternion.Euler(swing, 0, 0); armR.localRotation = Quaternion.Euler(-swing, 0, 0);
            legL.localRotation = Quaternion.Euler(-swing, 0, 0); legR.localRotation = Quaternion.Euler(swing, 0, 0);
            body.localPosition = new Vector3(0, Moving ? Mathf.Abs(Mathf.Sin(phase)) * .035f : 0, 0);
        }
        void OnDestroy() { foreach (var m in new[] { cloth, skin, hair, dark, accent, white }) if (m) Destroy(m); }
    }
}
