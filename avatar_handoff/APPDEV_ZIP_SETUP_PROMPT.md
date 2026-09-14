# GPT-Assisted Takeover ZIP Setup Prompt

Paste the text below into Earle's brand-new ChatGPT account with the takeover ZIP supplied.

---

You are helping initialize an existing VoxGest engineering handoff. Do not redesign or reinterpret the project. First inspect the supplied takeover ZIP and `START_HERE.txt`.

Then provide copy/paste-ready PowerShell commands, one verified phase at a time, to:

1. create `C:\VOXGEST_APPDEV` without deleting existing work;
2. extract the ZIP to a safe `C:` directory while preserving its `VOXGEST_APPDEV_TAKEOVER` root;
3. run `VERIFY_PACKAGE.ps1` from that root and stop on any missing file, checksum mismatch, prohibited binary, or corrupt archive evidence;
4. run `INSTALL_AND_BOOTSTRAP.ps1`;
5. verify Git remote, branch `avatar/astra-calibration-20260914`, HEAD, and worktree;
6. locate the developer-owned purchased Avatar on a safe `C:` path without moving, deleting, uploading, or committing it;
7. run the repository's safe Avatar asset inventory script;
8. open `C:\VOXGEST_APPDEV\LatestVoxGest` in ChatGPT Desktop/Codex/Astra;
9. paste the official instruction from `avatar_handoff\APPDEV_ASTRA_BOOTSTRAP_PROMPT.md`.

Do not access D:. Do not move/delete the developer's purchased source. Do not invent missing assets. Do not reset/discard a dirty Git worktree. Prefer idempotent commands, explicit paths, no wild-card deletion, and an observable verification after every phase. Stop and report exact failures if verification fails.

For the normal ChatGPT project conversation, load `avatar_handoff\APPDEV_CHATGPT_SOL_BOOTSTRAP.md` after repository setup.

---
