# Pocket Claude

A private, password-protected chat app for your phone. It runs on Claude (Anthropic) with live web search and page reading, so answers can draw on current information and show their sources.

## What's in here

| File | Purpose |
|---|---|
| `public/index.html` | The chat screen you see on your phone |
| `api/chat.js` | The server function that checks your password and talks to Claude |
| `public/manifest.webmanifest`, icons | Let you add it to your home screen like an app |
| `dev-server.js` | Optional: run it on a computer for testing |

## Set it up (about 15 minutes, no coding)

### 1. Get an Anthropic API key
1. Go to https://console.anthropic.com and sign up.
2. Under **Billing**, add a card and a small amount of credit (for example US$10). Set a monthly spend limit while you're there.
3. Under **API Keys**, create a key and copy it. It starts with `sk-ant-`. Treat it like a password and never paste it into a chat or email.

### 2. Deploy to Vercel (free)
1. Go to https://vercel.com and sign up with your GitHub account.
2. Click **Add New → Project** and import this repository (`hellow-world`).
3. Before you click Deploy, open **Environment Variables** and add two:
   - `ANTHROPIC_API_KEY` = the key from step 1
   - `APP_PASSWORD` = a long password you choose for the app
4. Click **Deploy**. Vercel gives you a web address such as `https://hellow-world-xyz.vercel.app`.

### 3. Put it on your phone
- **iPhone:** open the address in Safari, tap **Share → Add to Home Screen**.
- **Android:** open it in Chrome, tap **⋮ → Add to Home screen** (or Install app).

Open it from the home screen, enter your `APP_PASSWORD`, and start asking.

## Using it
- Ask anything. When a question needs current facts, it searches the web and lists **Sources** under the answer.
- **New chat** clears the conversation.
- **Lock** clears the conversation and forgets the password on that phone.

## Cost
You pay Anthropic per use. A typical question with a web search costs a few cents. Check usage in the Anthropic console and keep the spend limit on.

## Privacy and work material
- Conversations are stored only in your phone's browser until you tap New chat or Lock. The server keeps no chat log.
- Questions and answers are sent to Anthropic for processing, and search queries go to Anthropic's web search provider.
- This is a personal tool, not an ANZCA-approved system. Check ANZCA's IT and privacy policies before using it with work content. Keep confidential, personal or sensitive material out of it (for example trainee details, unreleased funding information, or anything marked confidential).
- Anyone with the web address and password can use your credit, so pick a strong password and change it in Vercel if it leaks.

## Changing things
- **Model:** `MODEL` in `api/chat.js` (currently `claude-opus-5-5`).
- **Tone and behaviour:** the `systemPrompt()` text in `api/chat.js`.
- **Search location:** `user_location` in `api/chat.js` (set to Sydney).

## Local testing (optional)
```bash
npm install
ANTHROPIC_API_KEY=sk-ant-... APP_PASSWORD=choose-one npm run dev
# open http://localhost:3000
```
