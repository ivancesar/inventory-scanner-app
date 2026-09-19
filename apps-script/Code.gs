// Inventory Scanner — paste this whole file into Extensions → Apps Script of the target sheet.
// Setup steps: see README.md.

var TAB = 'Scans';
var HEADER = ['Timestamp', 'Code', 'Area', 'User'];
var MAX_ROWS = 5000;
var MAX_LEN = 500;

function doGet() {
  return json({ ok: true, sheet: SpreadsheetApp.getActiveSpreadsheet().getName() });
}

function doPost(e) {
  var body;
  try {
    body = JSON.parse(e.postData.contents);
  } catch (err) {
    return json({ ok: false, error: 'Invalid JSON' });
  }
  var error = validate(body);
  if (error) return json({ ok: false, error: error });

  var lock = LockService.getScriptLock();
  lock.waitLock(30000);
  try {
    var props = PropertiesService.getScriptProperties();
    var key = 'batch:' + body.batchId;
    if (props.getProperty(key)) return json({ ok: true, added: 0, duplicate: true });

    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var sheet = ss.getSheetByName(TAB) || ss.insertSheet(TAB);
    if (sheet.getLastRow() === 0) sheet.appendRow(HEADER);
    var rows = body.rows.map(toSheetRow);
    sheet.getRange(sheet.getLastRow() + 1, 1, rows.length, HEADER.length).setValues(rows);
    SpreadsheetApp.flush();
    // ponytail: one property per batch, never pruned. Script properties cap at ~500KB (~10k batches); prune old keys if a sheet ever gets near that.
    props.setProperty(key, '1');
    return json({ ok: true, added: rows.length });
  } finally {
    lock.releaseLock();
  }
}

// Returns an error message, or '' when the body is valid.
function validate(body) {
  if (!body || typeof body.batchId !== 'string' || !body.batchId || body.batchId.length > 100) return 'Missing batchId';
  if (!Array.isArray(body.rows) || body.rows.length === 0) return 'No rows';
  if (body.rows.length > MAX_ROWS) return 'Too many rows (max ' + MAX_ROWS + ')';
  for (var i = 0; i < body.rows.length; i++) {
    var r = body.rows[i];
    if (!Array.isArray(r) || r.length !== HEADER.length) return 'Row ' + (i + 1) + ': expected ' + HEADER.length + ' columns';
    for (var j = 0; j < r.length; j++) {
      if (typeof r[j] !== 'string' || r[j].length > MAX_LEN) return 'Row ' + (i + 1) + ': bad value';
    }
    if (!/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(r[0])) return 'Row ' + (i + 1) + ': bad timestamp';
  }
  return '';
}

// Timestamp stays parseable as a date; Code/Area/User get a leading ' so Sheets
// keeps them as literal text (leading zeros survive, "=..." is never a formula).
function toSheetRow(r) {
  return [r[0], "'" + r[1], "'" + r[2], "'" + r[3]];
}

function json(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}

if (typeof module !== 'undefined') module.exports = { validate: validate, toSheetRow: toSheetRow };
