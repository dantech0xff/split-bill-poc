/**
 * The JSON Schema the model's answer must follow (Claude structured outputs). The app's
 * `BillAnalysisParser` reads exactly this shape, so a change here must keep it readable there.
 *
 * Kept within what structured outputs support: every object lists all of its keys as required and
 * forbids extra ones, there are no numeric or string length constraints, and only two fields are
 * nullable. Properties keep their order, because the model writes them in this order.
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
      kind: enumOf("tax", "service_charge", "tip", "discount", "rounding", "other"),
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
