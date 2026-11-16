/**
 * The JSON Schemas the model's answers must follow (Claude structured outputs): [BILL_SCHEMA] for a
 * bill read and [REVISION_SCHEMA] for an edit of a split. The app's `BillAnalysisParser` reads
 * exactly the shape of a bill read, so a change there must keep it readable by the app; an edit's
 * answer is checked and reshaped by the proxy before the app gets it.
 *
 * Kept within what structured outputs support: every object lists all of its keys as required and
 * forbids extra ones, there are no numeric or string length constraints, a list can ask for no
 * length but "at least one", and few fields are nullable or a choice of shapes. Properties keep
 * their order, because the model writes them in this order.
 */

type Schema = Record<string, unknown>;

function obj(properties: Record<string, Schema>): Schema {
  return { type: "object", properties, required: Object.keys(properties), additionalProperties: false };
}

function array(items: Schema): Schema {
  return { type: "array", items };
}

function string(description: string): Schema {
  return { type: "string", description };
}

function number(description: string): Schema {
  return { type: "number", description };
}

function integer(description: string): Schema {
  return { type: "integer", description };
}

function boolean(description: string): Schema {
  return { type: "boolean", description };
}

function nullable(type: string, description: string): Schema {
  return { type: [type, "null"], description };
}

function enumOf(...values: string[]): Schema {
  return { type: "string", enum: values };
}

/** A list of at least one [items]: the only length structured outputs can ask for. */
function nonEmpty(items: Schema): Schema {
  return { type: "array", items, minItems: 1 };
}

function anyOf(...schemas: Schema[]): Schema {
  return { anyOf: schemas };
}

/** The kinds of a charge, on a bill as read and in a charge an edit adds. */
export const CHARGE_KINDS = ["tax", "service_charge", "tip", "discount", "rounding", "other"] as const;

/** What a bill is for, as the contract's spending categories name it; the app shows its totals by them. */
export const BILL_CATEGORIES = ["food_and_drink", "groceries", "transport", "lodging", "entertainment", "shopping", "utilities", "rent", "other"] as const;

export const BILL_SCHEMA: Schema = obj({
  status: enumOf("ok", "not_a_bill", "unreadable"),
  merchant: string("Business name, or an empty string when it is not visible."),
  date: string("Bill date as YYYY-MM-DD, or an empty string when it is not visible."),
  currency: string("ISO 4217 code such as VND or USD."),
  amount_decimals: integer('Decimal places the bill prints its prices with: 0 for "70.000 đ" or "Rp 33.000", 2 for "$12.50".'),
  items: array(
    obj({
      name: string("Item name as printed."),
      quantity: number("Units bought; 1 when the bill does not show a quantity."),
      unit_price: nullable("number", "Price of one unit, or null when the bill does not show it."),
      amount: number("Line total for this item."),
    }),
  ),
  charges: array(
    obj({
      label: string('Label as printed, such as "VAT 8%".'),
      kind: enumOf(...CHARGE_KINDS),
      amount: number("Positive when it is added to the bill, negative when it is taken off."),
    }),
  ),
  total: number("Final amount to pay."),
  category: enumOf(...BILL_CATEGORIES),
  split: obj({
    people: array(string("Another person named in the user's instruction, as the user wrote it. Never the user.")),
    user_included: boolean("False only when the user says they are not taking part."),
    people_count: nullable("integer", "Total number of people including the user, or null when the user does not say."),
    item_assignments: array(
      obj({
        item_index: integer("Zero-based position of the item in items."),
        rule: enumOf("only", "all_except"),
        people: array(string("Another person the rule names, as the user wrote the name. Never the user.")),
        includes_user: boolean("True when the user is one of the people the rule names."),
      }),
    ),
  }),
  warnings: array(string("A short note about something the user should double-check.")),
  message: string("One short sentence for the user."),
});

/** The ways the whole bill can be split, as an edit names them. */
export const SPLIT_MODES = ["equal", "by_item", "by_shares", "by_percent", "by_amount"] as const;

/** The ids an edit gives the people it adds, in order; later edits name a new person by it. */
export const PERSON_REFS = ["new1", "new2", "new3", "new4", "new5", "new6", "new7", "new8", "new9"] as const;

const ITEM_ID = string("The id of an item of the bill.");
const PERSON_ID = string('The id of a participant of the split, or the ref of a person an earlier add_person edit added, such as "new1".');
const CHARGE_LABEL = string('A short label in the language of the instruction, such as "Tip".');

/** One edit: its op, then its fields. */
function edit(op: string, fields: Record<string, Schema>): Schema {
  return obj({ op: enumOf(op), ...fields });
}

/**
 * A number for each of some participants. Structured outputs cannot take an object whose keys are
 * not known in advance, so each participant is a pair, and the proxy makes the object of them.
 */
function perPerson(field: string, description: string): Schema {
  return nonEmpty(obj({ participantId: PERSON_ID, [field]: integer(description) }));
}

export const REVISION_SCHEMA: Schema = obj({
  question: nullable("string", "One short question in the language of the instruction, when it is too unclear to act on; null otherwise."),
  edits: array(
    anyOf(
      edit("item_sharers", { itemId: ITEM_ID, participantIds: nonEmpty(PERSON_ID) }),
      edit("item_shares", { itemId: ITEM_ID, shares: perPerson("shares", "A whole-number share, 1 or more.") }),
      edit("split_mode", { mode: enumOf(...SPLIT_MODES) }),
      edit("whole_shares", { shares: perPerson("shares", "A whole-number share, 0 or more.") }),
      edit("whole_percent", { basisPoints: perPerson("basisPoints", "Hundredths of a percent; together exactly 10000.") }),
      edit("whole_amounts", { amounts: perPerson("amountMinor", "An amount in minor units; together exactly the bill's totalMinor.") }),
      edit("add_person", { ref: enumOf(...PERSON_REFS), name: string("The new person's name as the user wrote it.") }),
      edit("remove_person", { participantId: PERSON_ID }),
      edit("set_sharing", { participantId: PERSON_ID, sharing: boolean("False when they take part without sharing the bill, true when they share it again.") }),
      edit("add_charge", { kind: enumOf(...CHARGE_KINDS), label: CHARGE_LABEL, amountMinor: integer("The amount in minor units, 1 or more.") }),
      edit("add_charge", {
        kind: enumOf(...CHARGE_KINDS),
        label: CHARGE_LABEL,
        percentBasisPoints: integer("A percentage of the items' total, in hundredths of a percent: 1 to 10000."),
      }),
      edit("set_payer", { participantId: PERSON_ID }),
      edit("item_amount", { itemId: ITEM_ID, amountMinor: integer("The item's right line total in minor units, 0 or more.") }),
      edit("item_quantity", { itemId: ITEM_ID, quantity: string('The right quantity, more than 0, as a decimal number written as text, such as "2" or "0.5".') }),
    ),
  ),
  explanation: string("One short sentence, at most 200 characters, in the language of the instruction: what the edits change, never what anyone owes."),
});
