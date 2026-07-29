// iOS availability chip. The app is not on the App Store yet, so this deliberately
// does NOT imitate Apple's black "Download on the App Store" badge — a lookalike
// badge promises a download that doesn't exist and misuses Apple's marketing mark.
// Until then it reads as a status pill in our own palette and points at the
// waitlist. Set NEXT_PUBLIC_APP_STORE_URL to the real listing for launch: the chip
// switches to the store treatment, and that is the moment to drop in Apple's
// official badge asset to match their marketing guidelines.
const APP_STORE_URL = process.env.NEXT_PUBLIC_APP_STORE_URL || "#waitlist";

export default function AppStoreBadge() {
  const live = APP_STORE_URL !== "#waitlist";
  return (
    <a
      className={`appstore ${live ? "is-live" : ""}`}
      href={APP_STORE_URL}
      aria-label={live ? "Download on the App Store" : "iOS coming soon — join the waitlist"}
    >
      <svg width="20" height="24" viewBox="0 0 20 24" fill="currentColor" aria-hidden="true">
        <path d="M16.36 12.72c-.02-2.03 1.66-3 1.73-3.05-.94-1.38-2.4-1.57-2.92-1.59-1.24-.13-2.43.73-3.06.73-.63 0-1.6-.71-2.64-.69-1.36.02-2.61.79-3.31 2-1.41 2.45-.36 6.08 1.01 8.07.67.97 1.47 2.06 2.51 2.02 1.01-.04 1.39-.65 2.61-.65 1.22 0 1.56.65 2.63.63 1.09-.02 1.78-.99 2.44-1.97.77-1.13 1.09-2.22 1.11-2.28-.02-.01-2.13-.82-2.16-3.25zM14.3 6.53c.56-.68.94-1.62.83-2.56-.81.03-1.79.54-2.37 1.21-.52.6-.97 1.56-.85 2.48.9.07 1.83-.46 2.39-1.13z" />
      </svg>
      <span className="appstore-txt">
        {live ? (
          <>
            <small>Download on the</small>
            App Store
          </>
        ) : (
          <>
            <small>Coming soon</small>
            iOS app
          </>
        )}
      </span>
    </a>
  );
}
