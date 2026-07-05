import { ImageResponse } from "next/og";

// Branded 1200×630 share image, generated on demand (no static asset needed).
export const alt = "CereBro — your quiet space for daily mental fitness";
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

export default function OpengraphImage() {
  return new ImageResponse(
    (
      <div
        style={{
          width: "100%",
          height: "100%",
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          justifyContent: "center",
          background: "linear-gradient(160deg, #1a1440 0%, #0e0c22 100%)",
          color: "#f4f6ff",
        }}
      >
        <div
          style={{
            width: 128,
            height: 128,
            borderRadius: 999,
            display: "flex",
            marginBottom: 44,
            background:
              "radial-gradient(circle at 36% 30%, #ffffff, #8a7bf0 50%, #a68bff 100%)",
            boxShadow: "0 0 90px rgba(111,123,247,0.7)",
          }}
        />
        <div style={{ display: "flex", fontSize: 88, fontWeight: 700, letterSpacing: "-0.03em" }}>
          CereBro
        </div>
        <div style={{ display: "flex", fontSize: 34, color: "#aeb6e0", marginTop: 20 }}>
          Your quiet space for a calmer mind
        </div>
      </div>
    ),
    { ...size }
  );
}
