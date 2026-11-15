-- Which participant of the bill each claim of a bill link is, as the owner matched them in settling
-- the split, so that the page can tell the people who claimed items whose amount is theirs: a JSON
-- object from claim ids to participant ids, its keys sorted, so that the same matches are always
-- written the same way. Null until the split is settled, and when the owner matched no claim. It
-- goes with the amounts, as `paid_by` does: whatever drops them drops it.
ALTER TABLE shares ADD COLUMN matches TEXT CHECK (matches IS NULL OR amounts IS NOT NULL);
