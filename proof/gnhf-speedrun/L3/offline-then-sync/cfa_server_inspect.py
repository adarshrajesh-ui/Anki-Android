"""M4 helper: full-download the current server collection into a fresh temp
desktop profile and report revlog state, so we can confirm a PHONE-originated
offline review reached the server after reconnect+sync. Read-only wrt the
server (download only; never uploads)."""
from __future__ import annotations
import json, sys, tempfile
sys.path.insert(0, "pylib"); sys.path.insert(0, "out/pylib")
from anki.collection import Collection
from anki import cfa_sync as cs

INFO = json.load(open("/tmp/cfa-syncserver/server-info.json"))

def main() -> int:
    look_for = int(sys.argv[1]) if len(sys.argv) > 1 else None
    tmp = tempfile.mkdtemp(prefix="cfa-inspect-")
    col = Collection(f"{tmp}/collection.anki2")
    try:
        auth = col.sync_login(INFO["user"], INFO["password"], INFO["host_endpoint"])
        cs.force_full_download(col, auth)
        col.close(); col = Collection(f"{tmp}/collection.anki2")
        revlog = col.db.scalar("select count(*) from revlog")
        cards = col.db.scalar("select count(*) from cards")
        last5 = col.db.all("select id,cid,ease,type,ivl from revlog order by id desc limit 5")
        out = {"server_revlog": revlog, "server_cards": cards,
               "last5_revlog": last5}
        if look_for is not None:
            hit = col.db.scalar("select count(*) from revlog where id=?", look_for)
            out["looking_for_id"] = look_for
            out["found_on_server"] = bool(hit)
        print("SERVER_STATE " + json.dumps(out))
        return 0
    finally:
        try: col.close()
        except Exception: pass

if __name__ == "__main__":
    raise SystemExit(main())
