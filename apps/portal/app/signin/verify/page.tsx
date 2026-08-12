import Link from "next/link";
import { Notice } from "@/components/ui";

/**
 * AUTH-02 — Multi-factor verification.
 *
 * Same rule as AUTH-01: the layout exists, the verification does not. The code
 * field is disabled so nothing can look like it was accepted.
 */
export const metadata = { title: "Verify your identity · CereBro for Organisations" };

export default function VerifyPage() {
  return (
    <>
      <div className="eyebrow">Multi-factor authentication</div>
      <h1>Verify your identity.</h1>
      <p className="lede">Enter the six-digit code from your authenticator or email.</p>

      <Notice tone="danger" icon="!">
        <b>This screen verifies nothing.</b>
        <br />
        There is no factor to check against and no session to establish. It is here so the
        access flow can be reviewed before it is built.
      </Notice>

      <div className="card" style={{ maxWidth: 520 }}>
        <label>
          <span className="label">Verification code</span>
          <input className="field" inputMode="numeric" maxLength={6} placeholder="000000" disabled />
        </label>
        <div className="toolbar">
          <Link className="btn secondary" href="/signin">Back to sign in</Link>
        </div>
      </div>
    </>
  );
}
