"""Desktop side of the M2 reverse-sync demo.

Downloads the current server collection into a fresh temp desktop profile,
makes ONE identifiable review on a specific CFA card, and uploads it back to
the running local anki-sync-server. Prints the causal marker (card id, reps
before/after, revlog counts) so the phone side can be checked against it.

Run with the ankiCFA desktop pyenv:
    out/pyenv/bin/python /tmp/cfa_desktop_review.py
"""

from __future__ import annotations

import json
import sys
import tempfile
import time

sys.path.insert(0, "pylib")
sys.path.insert(0, "out/pylib")

from anki.collection import Collection
from anki import cfa_sync as cs

SERVER_INFO = json.load(open("/tmp/cfa-syncserver/server-info.json"))
ENDPOINT = SERVER_INFO["host_endpoint"]
USER = SERVER_INFO["user"]
PASSWORD = SERVER_INFO["password"]


def main() -> int:
    tmp = tempfile.mkdtemp(prefix="cfa-desktop-")
    col = Collection(f"{tmp}/collection.anki2")
    try:
        auth = col.sync_login(USER, PASSWORD, ENDPOINT)
        # Pull the authoritative server state to the desktop.
        cs.force_full_download(col, auth)
        col.close()
        col = Collection(f"{tmp}/collection.anki2")

        before_revlog = col.db.scalar("select count(*) from revlog")
        # Pick a concrete, identifiable CFA card: the lowest-id card in a real
        # CFA note (deck name contains 'CFA'), so the phone can look it up.
        cid = col.db.scalar(
            "select c.id from cards c join decks d on c.did=d.id "
            "where d.name like '%CFA%' order by c.id limit 1"
        )
        if cid is None:
            cid = col.db.scalar("select id from cards order by id limit 1")
        card = col.get_card(cid)
        reps_before = card.reps
        # Move it into the review queue so answering is a real graded review.
        col.sched.set_due_date([cid], "0")
        col.decks.set_current(card.did)

        # Answer it Good through the real scheduler (writes a revlog row).
        target = col.get_card(cid)
        target.start_timer()
        col.sched.answerCard(target, 3)  # 3 = Good in v3

        after_card = col.get_card(cid)
        reps_after = after_card.reps
        mid_revlog = col.db.scalar("select count(*) from revlog")
        last_rev = col.db.scalar(
            "select id from revlog where cid=? order by id desc limit 1", cid
        )

        # Upload the review to the server.
        col.save()
        auth2 = col.sync_login(USER, PASSWORD, ENDPOINT)
        out = cs.sync(col, auth2)
        after_revlog = col.db.scalar("select count(*) from revlog")

        marker = {
            "card_id": cid,
            "deck_id": card.did,
            "reps_before": reps_before,
            "reps_after": reps_after,
            "revlog_before_dl": before_revlog,
            "revlog_after_review": mid_revlog,
            "revlog_after_sync": after_revlog,
            "last_revlog_id_for_card": last_rev,
            "sync_result": out,
            "reviewed_at_ms": int(time.time() * 1000),
        }
        print("DESKTOP_REVIEW_MARKER " + json.dumps(marker))
        return 0
    finally:
        try:
            col.close()
        except Exception:
            pass


if __name__ == "__main__":
    raise SystemExit(main())
