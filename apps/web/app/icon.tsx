import { ImageResponse } from "next/og";

// Generated favicon: the brand orb (no static .ico needed).
export const size = { width: 32, height: 32 };
export const contentType = "image/png";

export default function Icon() {
  return new ImageResponse(
    (
      <div
        style={{
          width: "100%",
          height: "100%",
          display: "flex",
          borderRadius: 999,
          background:
            "radial-gradient(circle at 36% 30%, #ffffff, #6f7bf7 55%, #9b6bff 100%)",
        }}
      />
    ),
    { ...size }
  );
}
