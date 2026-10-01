"""engine <command>

  import-signals   load the signals folder into engine.db
  evaluate         compare every ranker on the logged plays and suggestions (add --json for raw output)
  serve            run the HTTP API on port 8097
"""
import json
import sys

from engine import config, data as engine_data, db as engine_db, events


def main():
    cmd = sys.argv[1] if len(sys.argv) > 1 else "help"
    cfg = config.load()
    if cmd == "import-signals":
        from engine.jobs import import_signals
        print(json.dumps(import_signals.main(cfg, engine_db.connect(cfg.engine_db)), indent=2, ensure_ascii=False))
    elif cmd == "evaluate":
        from engine.evaluate import evaluate
        examples = events.load(engine_db.read_only(cfg.social_db))
        report = evaluate(examples, engine_data.load(engine_db.connect(cfg.engine_db)))
        if "--json" in sys.argv:
            print(json.dumps(report, indent=2))
            return
        print(f"{report['examples']} examples, {report['labelled_known_songs']} labelled on live songs: "
              f"train {report['train']}, test {report['test']}")
        for name, groups in report["rankers"].items():
            for group, m in groups.items():
                if group != "weights":
                    print(f"  {name:12} {group:14} " + "  ".join(f"{k} {v}" for k, v in m.items()))
            if groups.get("weights"):
                print(f"  {'':12} weights        {groups['weights']}")
    elif cmd == "serve":
        import uvicorn
        uvicorn.run("engine.api:app", host="0.0.0.0", port=8097)
    else:
        print(__doc__)


if __name__ == "__main__":
    main()
