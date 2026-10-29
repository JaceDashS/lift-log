# Edit Mode Scenarios
- [Design](../../../design/workout/10-edit-mode.md)
These are manual scenarios, not automated coverage. Run selection checks in both editing surfaces.

## Short Press
- Input: No selection.
- Steps: Tap set.
- Expected result: Only set is selected, handles appear, no orange highlight.

## Long Press
- Input: Set and total are visible.
- Steps: Long press set, drag across total and release.
- Expected result: Both remain selected and orange.

## Selected Element
- Input: Set and total are selected.
- Steps: Tap set.
- Expected result: Multi selection remains.

## Blank Space
- Input: Single or multi selection exists.
- Steps: Tap blank space including edit-panel whitespace.
- Expected result: All selection clears and default visuals return.

## Another Element
- Input: Selection exists with another unselected element visible.
- Steps: Tap the unselected element.
- Expected result: It becomes the single selected element.

## One Element Release
- Input: One element is being long pressed.
- Steps: Release without adding another element.
- Expected result: Single selection visuals remain.

## Overlay Controls
- Input: Normal overlay above another app.
- Steps: Tap a button in the underlying app, then use an overlay control.
- Expected result: Underlying app receives its tap and separate overlay controls are usable.

## Editing Transition
- Input: Normal overlay is running.
- Steps: Enter Edit, manipulate an element and leave editing.
- Expected result: Editing receives touches and normal overlay returns to touch through.

## Clear Log
- Input: Selection debug log exists.
- Steps: Press Clear Log on the main screen.
- Expected result: The app attempts deletion of the internal selection-debug.log.
