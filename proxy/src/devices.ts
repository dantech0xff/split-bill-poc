import { signedIn } from "./identity";
import { inputOf, uuidOf } from "./input";
import { POLICY } from "./policy";
import { emptyResponse, errorResponse } from "./respond";

const MAX_TOKEN_CHARS = 4096;

/**
 * `PUT /v1/devices/{deviceId}` with `{"token"}`: registers the FCM token of one install of the app
 * for the caller, or replaces it. A token belongs to one install and one account: when a phone
 * changes accounts, the earlier registration of its token goes, so the earlier account's
 * notifications stop reaching it. An account keeps its ten most recent devices.
 */
export const putDeviceRoute = signedIn(async ({ request, env, params, account }) => {
  const deviceId = uuidOf(params[0]);
  if (deviceId === null) return errorResponse(400, "input");
  const input = await inputOf(request, POLICY.maxSharedBodyBytes, (body) => {
    const token = body.token;
    return typeof token === "string" && token.length >= 1 && token.length <= MAX_TOKEN_CHARS ? { token } : null;
  });
  if (!input.ok) return input.response;
  const { token } = input.value;
  const db = env.DB;
  await db.batch([
    db.prepare("DELETE FROM devices WHERE token = ?1 AND id <> ?2").bind(token, deviceId),
    db
      .prepare(
        `INSERT INTO devices (id, account, token, updated_at) VALUES (?1, ?2, ?3, ?4)
         ON CONFLICT (id) DO UPDATE SET account = excluded.account, token = excluded.token, updated_at = excluded.updated_at`,
      )
      .bind(deviceId, account, token, Date.now()),
    db
      .prepare("DELETE FROM devices WHERE account = ?1 AND id NOT IN (SELECT id FROM devices WHERE account = ?1 ORDER BY updated_at DESC, id DESC LIMIT ?2)")
      .bind(account, POLICY.maxDevices),
  ]);
  return emptyResponse(204);
});

/** `DELETE /v1/devices/{deviceId}`: the install stops getting the caller's notifications. Answers 204 whether or not it was registered. */
export const deleteDeviceRoute = signedIn(async ({ env, params, account }) => {
  const deviceId = uuidOf(params[0]);
  if (deviceId === null) return errorResponse(404, "not_found");
  await env.DB.prepare("DELETE FROM devices WHERE id = ?1 AND account = ?2").bind(deviceId, account).run();
  return emptyResponse(204);
});
