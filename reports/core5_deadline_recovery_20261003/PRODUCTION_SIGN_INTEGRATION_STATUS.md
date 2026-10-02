# Modern SIGN integration status

`PRODUCTION_SIGN_CHANGED=NO`. The modern `com.voxgest.dryrun` SIGN implementation still uses its existing profile/continuous one-hand path. The isolated Recognition Lab is the only place with the new user-triggered controlled window. No automatic text or speech was added, no model was promoted, and CONVERSATION, BOARD, LISTEN, GUIDE, and Avatar were not edited.

Do not integrate until the lab passes Samsung positives and negatives with a documented model/rule selected using permitted development evidence. If it qualifies, the minimal future panel states are READY → GET READY → SIGN NOW → RECOGNIZING → RECOGNIZED or SIGN AGAIN, with KEEP HAND VISIBLE and MOVE BACK guidance for measured capture-quality failures. Show a recognized token only after acceptance; allow user-triggered SPEAK and RETRY. Never auto-speak directly from raw TFLite output. Developer diagnostics remain in the lab. Display interpolation may smooth overlays but must not feed the classifier.

Current blocker: Samsung USB debugging authorization and then controlled physical qualification. Historical false accepts make a model swap or direct production exposure unjustified.
