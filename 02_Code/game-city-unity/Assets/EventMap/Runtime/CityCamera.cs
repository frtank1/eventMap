using UnityEngine;

namespace EventMap.City
{
    public class CityCamera : MonoBehaviour
    {
        public float Distance = 260, Yaw = -25, Pitch = 48;
        public Vector3 Target = new Vector3(0, 22, 0);
        public Transform Follow;
        public bool InputBlocked;
        Vector3 smoothTarget;
        float smoothDistance;
        bool dragging;
        Vector3 lastMouse;
        public Camera Camera { get; private set; }
        public void Focus(Vector3 point, float distance) { Follow = null; Target = point; Distance = Mathf.Clamp(distance, 4, 5500); }
        public void FollowAvatar(Transform who, float distance = 8) { Follow = who; Distance = distance; Pitch = 24; }
        void Awake()
        {
            Camera = GetComponent<Camera>(); smoothTarget = Target; smoothDistance = Distance;
            Camera.nearClipPlane = .15f; Camera.farClipPlane = 15000; Camera.fieldOfView = 48;
        }
        void LateUpdate()
        {
            // The left inspector is a desktop pilot UI, outside the camera viewport.
            Camera.rect = new Rect(.28f, 0, .72f, 1);
            bool onMap = Input.mousePosition.x > Screen.width * .28f;
            if (!InputBlocked && onMap)
            {
                Distance = Mathf.Clamp(Distance * Mathf.Exp(-Input.mouseScrollDelta.y * .12f), 4, 5500);
                if (Input.GetMouseButtonDown(0) || Input.GetMouseButtonDown(1)) { dragging = true; lastMouse = Input.mousePosition; }
                if (dragging)
                {
                    var delta = Input.mousePosition - lastMouse; lastMouse = Input.mousePosition;
                    if (Input.GetMouseButton(1) || Input.GetKey(KeyCode.LeftShift))
                    {
                        Follow = null;
                        var right = Quaternion.Euler(0, Yaw, 0) * Vector3.right;
                        var forward = Quaternion.Euler(0, Yaw, 0) * Vector3.forward;
                        Target -= (right * delta.x + forward * delta.y) * Distance * .0015f;
                    }
                    else if (Input.GetMouseButton(0)) { Yaw += delta.x * .25f; Pitch = Mathf.Clamp(Pitch - delta.y * .2f, 12, 78); }
                }
            }
            if (!Input.GetMouseButton(0) && !Input.GetMouseButton(1)) dragging = false;
            if (Follow) Target = Follow.position + Vector3.up * 1.15f;
            float blend = 1 - Mathf.Exp(-Time.deltaTime * 9);
            smoothTarget = Vector3.Lerp(smoothTarget, Target, blend); smoothDistance = Mathf.Lerp(smoothDistance, Distance, blend);
            transform.position = smoothTarget + Quaternion.Euler(Pitch, Yaw, 0) * new Vector3(0, 0, -smoothDistance);
            transform.LookAt(smoothTarget);
        }
    }
}
