# Velnox Marketplace & Mobile — Credentials & Environment Sync Guide

This document outlines the synchronized configuration of third-party credentials across the **Velnox Mobile Client** (Android Jetpack Compose), the **Backend Services** (Cloudflare Workers & Express API), and the **Web Frontends** (VelShop, VelSeller, VelCenter).

---

## 1. Native Mobile Client (Android Jetpack Compose)

The mobile client bakes public identifiers into `BuildConfig` (overridable via Gradle project properties) and fetches runtime configuration from `GET /api/auth/mobile/config`.

### Client Configuration (`app/build.gradle.kts` & `BackendConfig.kt`):
- **Google OAuth Web Client ID (`GOOGLE_WEB_CLIENT_ID`):**
  - Gradle Property: `-PvelnoxGoogleClientId=...`
  - Purpose: Passed to Google Credential Manager as `serverClientId` to mint Google ID tokens whose audience (`aud`) matches backend verification.
- **Stripe Publishable Key (`STRIPE_PUBLISHABLE_KEY`):**
  - Gradle Property: `-PvelnoxStripePublishableKey=...`
  - Purpose: Client-side card tokenization and Stripe Elements.
- **LINE Channel ID (`LINE_CHANNEL_ID`):**
  - Gradle Property: `-PvelnoxLineChannelId=...`
  - Purpose: LINE Login client identifier.

---

## 2. Server-Side Environment Variables (Render & Cloudflare Workers)

These secrets must **never** be committed to Git. Configure them directly in your hosting dashboard:

### 2.1 Database & Security
```env
DATABASE_URL=postgresql://<user>:<password>@<host>/<database>?sslmode=require&channel_binding=require
JWT_SECRET=<secure-random-jwt-secret>
```

### 2.2 Google Identity & Mobile Verification
```env
GOOGLE_CLIENT_ID=<your-web-client-id>.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=<your-google-client-secret>
MOBILE_GOOGLE_CLIENT_ID=<your-web-client-id>.apps.googleusercontent.com
```

### 2.3 Stripe Payments (Test Mode)
```env
STRIPE_MODE=test
STRIPE_SECRET_KEY=sk_test_<your-stripe-secret-key>
STRIPE_PUBLISHABLE_KEY=pk_test_<your-stripe-publishable-key>
STRIPE_WEBHOOK_SECRET=whsec_<your-stripe-webhook-secret>
```

### 2.4 Cloudflare Turnstile Bot Protection (Backend Verification)
```env
TURNSTILE_SECRET_KEY=<your-turnstile-secret-key>
```

### 2.5 LINE Login Authentication
```env
LINE_CHANNEL_ID=<your-line-channel-id>
LINE_CHANNEL_SECRET=<your-line-channel-secret>
```

---

## 3. Web Frontends Environment Variables (Vercel)

For your web storefronts (VelShop, VelSeller, VelCenter), set:
```env
VITE_TURNSTILE_SITE_KEY=<your-turnstile-site-key>
VITE_STRIPE_PUBLISHABLE_KEY=pk_test_<your-stripe-publishable-key>
VITE_GOOGLE_CLIENT_ID=<your-web-client-id>.apps.googleusercontent.com
```

---

## 4. Deployment Instructions

1. **For Render (Express Backend):**
   - Go to **Render Dashboard** -> Your Web Service -> **Environment**.
   - Add all key-value pairs listed in Section 2 above with your credentials.
   - Click **Save Changes** (Render will redeploy with new environment values).
2. **For Cloudflare Workers (`workers/api`):**
   - Run:
     ```bash
     wrangler secret put JWT_SECRET
     wrangler secret put STRIPE_SECRET_KEY
     wrangler secret put GOOGLE_CLIENT_SECRET
     wrangler secret put TURNSTILE_SECRET_KEY
     wrangler secret put LINE_CHANNEL_SECRET
     ```
3. **For Mobile App:**
   - The public client IDs are configured in build variables and verified with backend `/api/auth/mobile/config`.
