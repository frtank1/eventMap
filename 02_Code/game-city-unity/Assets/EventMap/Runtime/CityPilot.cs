using UnityEngine;

namespace EventMap.City
{
    public class CityPilot : MonoBehaviour
    {
        CityCamera orbit;
        PilotAvatar me, friend;
        AvatarAppearance appearance;
        CityPlace[] places;
        Light sun;
        bool edit, friendVisible, running, night;
        float progress, friendProgress;
        string notice = "Локальный тест. Геопозиция не запрашивается.", selected = "Байтерек";
        GUIStyle label, title, subtitle, button, small, marker;
        Vector2 scroll;
        Texture2D panel, card;
        const float UiWidth = 1280, UiHeight = 800;
        static readonly Vector3[] Walk = { new Vector3(-18, .18f, -55), new Vector3(18, .18f, -55), new Vector3(18, .18f, -115), new Vector3(-18, .18f, -115) };
        static readonly Vector3[] Drive = { new Vector3(-90, .35f, -160), new Vector3(90, .35f, -160), new Vector3(90, .35f, -235), new Vector3(-90, .35f, -235) };

        void Start()
        {
            Application.targetFrameRate = 60;
            orbit = Camera.main.GetComponent<CityCamera>();
            sun = GameObject.Find("Sun").GetComponent<Light>();
            places = FindObjectsByType<CityPlace>(FindObjectsSortMode.None);
            appearance = AvatarAppearance.Load();
            me = MakeAvatar("Мой персонаж", appearance, Walk[0]);
            friend = MakeAvatar("Демо-друг", new AvatarAppearance { outfit = 1, hairstyle = 1, skin = 2 }, Walk[2]);
            friend.gameObject.SetActive(false);
            SetNight(false);
        }
        PilotAvatar MakeAvatar(string name, AvatarAppearance look, Vector3 position)
        {
            var obj = new GameObject(name); obj.transform.position = position;
            var avatar = obj.AddComponent<PilotAvatar>(); avatar.Build(look); return avatar;
        }
        void Update()
        {
            if (!me) return;
            if (running && !edit) Advance(me, ref progress, me.Driving ? Drive : Walk, me.Driving ? 7.5f : 1.3f);
            me.Moving = running && !edit;
            if (friendVisible) { Advance(friend, ref friendProgress, Walk, 1.1f); friend.Moving = true; }
            me.SetDetail(Vector3.Distance(orbit.transform.position, me.transform.position) < 160);
            friend.SetDetail(Vector3.Distance(orbit.transform.position, friend.transform.position) < 160);
            if (Input.GetKeyDown(KeyCode.Escape)) { edit = false; orbit.Focus(new Vector3(0, 20, 0), 260); orbit.Pitch = 48; }
        }
        static void Advance(PilotAvatar avatar, ref float travel, Vector3[] points, float speed)
        {
            float total = 0;
            for (int i = 0; i < points.Length; i++) total += Vector3.Distance(points[i], points[(i + 1) % points.Length]);
            travel = (travel + Time.deltaTime * speed) % total;
            float remaining = travel;
            for (int i = 0; i < points.Length; i++)
            {
                var a = points[i]; var b = points[(i + 1) % points.Length]; float length = Vector3.Distance(a, b);
                if (remaining <= length)
                {
                    avatar.transform.position = Vector3.Lerp(a, b, remaining / length);
                    avatar.transform.rotation = Quaternion.Slerp(avatar.transform.rotation, Quaternion.LookRotation(b - a), 1 - Mathf.Exp(-Time.deltaTime * 8));
                    return;
                }
                remaining -= length;
            }
        }
        void SetNight(bool value)
        {
            night = value;
            sun.intensity = night ? .20f : 1.15f;
            sun.color = night ? new Color(.55f, .64f, 1) : new Color(1, .93f, .82f);
            RenderSettings.ambientLight = night ? new Color(.18f, .21f, .33f) : new Color(.57f, .63f, .68f);
            Camera.main.backgroundColor = night ? new Color(.026f, .039f, .075f) : new Color(.73f, .80f, .83f);
            RenderSettings.fog = true; RenderSettings.fogMode = FogMode.ExponentialSquared;
            RenderSettings.fogColor = Camera.main.backgroundColor; RenderSettings.fogDensity = night ? .00028f : .00016f;
            foreach (var light in GameObject.FindGameObjectsWithTag("Respawn")) light.GetComponent<Light>().enabled = night;
        }
        Texture2D Flat(Color color) { var t = new Texture2D(1, 1); t.SetPixel(0, 0, color); t.Apply(); return t; }
        void Styles()
        {
            if (label != null) return;
            panel = Flat(new Color(.055f, .075f, .12f)); card = Flat(new Color(.12f, .15f, .22f));
            label = new GUIStyle(GUI.skin.label) { fontSize = 17, wordWrap = true, normal = { textColor = new Color(.9f, .92f, .97f) } };
            small = new GUIStyle(label) { fontSize = 13, normal = { textColor = new Color(.59f, .67f, .77f) } };
            subtitle = new GUIStyle(label) { fontSize = 15, normal = { textColor = new Color(.72f, .64f, .98f) } };
            title = new GUIStyle(label) { fontSize = 32, fontStyle = FontStyle.Bold };
            button = new GUIStyle(GUI.skin.button) { fontSize = 16, alignment = TextAnchor.MiddleLeft, padding = new RectOffset(14, 14, 10, 10), fixedHeight = 42,
                normal = { background = card, textColor = Color.white }, hover = { background = card, textColor = new Color(.78f, .69f, 1) } };
            marker = new GUIStyle(button) { alignment = TextAnchor.MiddleCenter, fontSize = 14, fixedHeight = 32 };
        }
        bool Button(string text) => GUILayout.Button(text, button);
        void OnGUI()
        {
            if (!me) return;
            Styles();
            GUI.matrix = Matrix4x4.TRS(Vector3.zero, Quaternion.identity, new Vector3(Screen.width / UiWidth, Screen.height / UiHeight, 1));
            GUI.DrawTexture(new Rect(0, 0, 358, 800), panel);
            GUILayout.BeginArea(new Rect(24, 22, 310, 754));
            scroll = GUILayout.BeginScrollView(scroll, false, false);
            GUILayout.Label("EVENTMAP / UNITY PILOT", subtitle);
            GUILayout.Space(8); GUILayout.Label(edit ? "Мой персонаж" : "Город, где ты", title);
            GUILayout.Label("Астана · тест движка 01", small); GUILayout.Space(20);
            if (edit) AvatarEditor(); else CityControls();
            GUILayout.Space(16); GUILayout.Label(notice, small);
            GUILayout.Space(12); GUILayout.Label("Вращение — перетаскивание\nЗум — колесо / кнопки + −\nСдвиг — правая кнопка / Shift\nEsc — вернуться к Байтереку", small);
            GUILayout.EndScrollView(); GUILayout.EndArea();

            GUI.Label(new Rect(386, 24, 800, 35), selected, title);
            GUI.Label(new Rect(388, 65, 750, 24), edit ? "Внешность сохраняется только на этом устройстве" : "Наши модели · окружение демонстрационное · не навигация", small);
            if (GUI.Button(new Rect(1205, 24, 48, 42), "+", button)) orbit.Distance = Mathf.Max(4, orbit.Distance * .6f);
            if (GUI.Button(new Rect(1205, 74, 48, 42), "−", button)) orbit.Distance = Mathf.Min(5500, orbit.Distance / .6f);
            if (!edit)
            {
                Marker(me, "Ты · демо", new Color(.72f, .58f, 1));
                if (friendVisible) Marker(friend, "Друг · симуляция", new Color(.33f, .86f, .73f));
                foreach (var p in places) PlaceMarker(p);
            }
            GUI.Label(new Rect(390, 754, 825, 25), "ДЕМО • нет GPS и отправки координат • движение по тестовому маршруту", subtitle);
            // Map overlays must not also rotate the camera when clicked.
            Vector2 mouse = new Vector2(Input.mousePosition.x / Screen.width * UiWidth, (Screen.height - Input.mousePosition.y) / Screen.height * UiHeight);
            orbit.InputBlocked = mouse.y < 110 || mouse.y > 740;
        }
        void CityControls()
        {
            GUILayout.Label("МЕСТА", small);
            foreach (string id in new[] { "baiterek", "akorda", "arena" })
            {
                var p = System.Array.Find(places, entry => entry.placeId == id);
                if (p && Button(p.title + "    →")) { orbit.Focus(p.transform.position + Vector3.up * 20, p.viewDistance); orbit.Pitch = 48; selected = p.title; }
                GUILayout.Space(5);
            }
            GUILayout.Space(14); GUILayout.Label("ПЕРСОНАЖ", small);
            if (Button("Приблизить ко мне")) { orbit.FollowAvatar(me.transform, me.Driving ? 12 : 7); selected = "Мой персонаж"; }
            GUILayout.Space(5);
            if (Button("Изменить внешность")) { edit = true; me.SetDriving(false); me.transform.position = Walk[0]; progress = 0; orbit.FollowAvatar(me.transform, 4.8f); orbit.Yaw = 160; selected = "Твой образ в городе"; }
            GUILayout.Space(8);
            GUILayout.BeginHorizontal();
            if (Button(me.Driving ? "Пешком" : "• Пешком")) { me.SetDriving(false); progress = 0; me.transform.position = Walk[0]; }
            if (Button(me.Driving ? "• Машина" : "Машина")) { me.SetDriving(true); progress = 0; me.transform.position = Drive[0]; if (orbit.Follow == me.transform) orbit.Distance = 12; }
            GUILayout.EndHorizontal(); GUILayout.Space(5);
            if (Button(running ? "Пауза движения" : "Запустить движение")) running = !running;
            GUILayout.Space(14); GUILayout.Label("ДРУГ / ЛОКАЛЬНЫЙ ТЕСТ", small);
            if (Button(friendVisible ? "Скрыть демо-друга" : "Показать демо-друга"))
            {
                friendVisible = !friendVisible; friend.gameObject.SetActive(friendVisible);
                if (!friendVisible && orbit.Follow == friend.transform) orbit.FollowAvatar(me.transform);
            }
            if (friendVisible)
            {
                GUILayout.Space(5);
                if (Button("Приблизить к другу")) { orbit.FollowAvatar(friend.transform, 7); selected = "Демо-друг · не реальный человек"; }
            }
            GUILayout.Space(5);
            if (Button("Вставить код внешности друга"))
            {
                if (AvatarAppearance.TryCode(GUIUtility.systemCopyBuffer, out var imported))
                {
                    friend.Apply(imported); friendVisible = true; friend.gameObject.SetActive(true);
                    notice = "Внешность импортирована. Это не геошаринг: координат в коде нет.";
                }
                else notice = "Нужен код EMAV1 из редактора персонажа. Буфер не сохранён.";
            }
            GUILayout.Space(10); if (Button(night ? "Включить день" : "Включить ночь")) SetNight(!night);
        }
        void AvatarEditor()
        {
            GUILayout.Label("Цвет кожи", label); Palette(AvatarAppearance.Skin, ref appearance.skin);
            GUILayout.Space(10); GUILayout.Label("Цвет волос", label); Palette(AvatarAppearance.Hair, ref appearance.hair);
            GUILayout.Space(10); GUILayout.Label("Одежда", label); Palette(AvatarAppearance.Outfit, ref appearance.outfit);
            GUILayout.Space(12);
            if (Button("Причёска: " + new[] { "короткая", "длинная", "кепка" }[appearance.hairstyle])) { appearance.hairstyle = (appearance.hairstyle + 1) % 3; Changed(); }
            GUILayout.Space(6);
            if (Button(appearance.backpack ? "Рюкзак: включён" : "Рюкзак: выключен")) { appearance.backpack = !appearance.backpack; Changed(); }
            GUILayout.Space(20);
            if (Button("Скопировать код внешности")) { GUIUtility.systemCopyBuffer = appearance.ToCode(); notice = "Код внешности скопирован. Можешь передать его другу. Координат в нём нет."; }
            GUILayout.Space(6); if (Button("Готово · вернуться в город")) { edit = false; orbit.FollowAvatar(me.transform, 10); selected = "Мой персонаж"; }
            GUILayout.Space(12); GUILayout.Label("Пока это простой тестовый персонаж. Позже заменим его на качественную модель с одеждой и анимациями.", small);
        }
        void Palette(Color[] colors, ref int value)
        {
            GUILayout.BeginHorizontal();
            for (int i = 0; i < colors.Length; i++)
            {
                var old = GUI.backgroundColor; GUI.backgroundColor = colors[i] * 2;
                if (GUILayout.Button(value == i ? "●" : "", button, GUILayout.Width(48))) { value = i; Changed(); }
                GUI.backgroundColor = old;
            }
            GUILayout.EndHorizontal();
        }
        void Changed() { me.Apply(appearance); appearance.Save(); }
        void Marker(PilotAvatar who, string text, Color color)
        {
            var p = Camera.main.WorldToScreenPoint(who.transform.position + Vector3.up * 2.7f);
            if (p.z <= 0) return;
            var r = new Rect(p.x / Screen.width * UiWidth - 73, (1 - p.y / Screen.height) * UiHeight - 32, 146, 32);
            if (r.x < 362 || r.xMax > UiWidth || r.y < 100 || r.yMax > 730) return;
            var old = GUI.color; GUI.color = color;
            if (GUI.Button(r, text, marker)) { orbit.FollowAvatar(who.transform, who.Driving ? 12 : 7); selected = text; }
            GUI.color = old;
        }
        void PlaceMarker(CityPlace place)
        {
            if (orbit.Distance < 70) return;
            var p = Camera.main.WorldToScreenPoint(place.transform.position);
            if (p.z <= 0) return;
            var r = new Rect(p.x / Screen.width * UiWidth - 72, (1 - p.y / Screen.height) * UiHeight + 10, 144, 32);
            if (r.x < 362 || r.xMax > UiWidth || r.y < 100 || r.yMax > 720) return;
            if (GUI.Button(r, place.title, marker)) { orbit.Focus(place.transform.position + Vector3.up * 20, place.viewDistance); selected = place.title; }
        }
        void OnDestroy() { if (panel) Destroy(panel); if (card) Destroy(card); }
    }
}
