# Selection Debugging

## Purpose

This document records the blank-space deselection issue, the fix, and how to collect debug logs when the issue appears again.

## Issue Summary

- Symptom: after short-press selecting an element, tapping blank space did not clear selection.
- Observed cause: some blank-space taps produced `ACTION_DOWN` but did not reliably produce `ACTION_UP` in the previous deselection path.
- Effect: logic that depended on `ACTION_UP` could miss the deselect action.

## Current Behavior

- If a tap lands on a selectable element, selection logic for that element runs.
- If a tap lands anywhere else, selection is cleared.
- This blank-space definition includes the edit panel.
- The same rule applies to developer mode and overlay mode.

## Fix Summary

- Root touch tracing was moved to `TouchAwareFrameLayout.onPostDispatchTouchEvent`.
- Blank-space deselection now runs on blank-space `ACTION_DOWN`.
- `ACTION_UP` logging is still kept for debugging and verification.

## Debug Logging

Selection debug logs are written to:

- `/data/user/0/com.example.workouttimer/files/selection-debug.log`

Equivalent path on some devices:

- `/data/data/com.example.workouttimer/files/selection-debug.log`

The same messages are also emitted to Logcat with tag:

- `SelectionDebug`

## Clear Log Button

`Clear Log` is available on the main screen before entering developer mode.

Button order:

- `Start Workout`
- `Dev Mode`
- `Reset All Settings`
- `Clear Log`

Pressing `Clear Log` deletes `selection-debug.log`.

## Useful Commands

Delete the log:

```bash
adb shell run-as com.example.workouttimer rm files/selection-debug.log
```

Read the log:

```bash
adb shell run-as com.example.workouttimer cat files/selection-debug.log
```

Pull the log to the host machine:

```bash
adb exec-out run-as com.example.workouttimer cat files/selection-debug.log > selection-debug.log
```

## What To Check In Logs

Expected sequence for a successful blank-space deselect:

1. `postDispatch ACTION_DOWN`
2. `handleRootActionDown clearing selection`
3. `clearSelection`

If deselection fails, compare:

- whether `ACTION_DOWN` was logged
- whether the tap was treated as a selectable-view tap
- whether `clearSelection` was skipped because selection was already empty
## Example
- Input: Selected element and blank panel space.
- Steps: Tap blank space and inspect SelectionDebug logs.
- Expected result: Root ACTION_DOWN, clearing selection and clearSelection messages appear.

- [Design](../design/workout/10-edit-mode.md)
