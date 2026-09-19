// Run: node apps-script/Code.test.js
const assert = require('assert');
const { validate, toSheetRow } = require('./Code.gs');

const row = ['2026-09-19 10:00:00', '0012', 'Aisle 3', 'Ana'];
assert.strictEqual(validate({ batchId: 'b1', rows: [row] }), '');
assert.ok(validate({ rows: [row] }));
assert.ok(validate({ batchId: 'b1', rows: [] }));
assert.ok(validate({ batchId: 'b1', rows: [row.slice(0, 3)] }));
assert.ok(validate({ batchId: 'b1', rows: [[...row.slice(0, 3), 5]] }));
assert.ok(validate({ batchId: 'b1', rows: [['=IMPORTXML("x")', ...row.slice(1)]] }));
assert.ok(validate({ batchId: 'b1', rows: [[row[0], 'x'.repeat(501), row[2], row[3]]] }));
assert.ok(validate({ batchId: 'b1', rows: Array(5001).fill(row) }));
assert.ok(validate(null));

assert.deepStrictEqual(toSheetRow(['2026-09-19 10:00:00', '=1+1', '0012', 'Ana']),
  ['2026-09-19 10:00:00', "'=1+1", "'0012", "'Ana"]);

console.log('Code.gs ok');
