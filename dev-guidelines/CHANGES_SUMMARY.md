# License Plate Matching Changes - Numeric Digits Only

## Summary
Modified the Vehicle Recognition Android PoC to match license plates based on **numeric digits only**, excluding dashes and other formatting characters.

## Changes Made

### 1. Modified `VehicleMatcher.kt`
- **Location**: `shared/src/commonMain/kotlin/com/example/vehiclerecognition/domain/logic/VehicleMatcher.kt`
- **Key Changes**:
  - Added `extractNumericDigits()` function to extract only numeric characters
  - Added `licensePlatesMatch()` function for numeric-only comparison
  - Updated `isMatch()` to use numeric comparison for all license plate modes
  - Modified validation to check digit count (7-8 digits) instead of exact format matching

### 2. Updated Test Cases
- **Location**: `shared/src/commonTest/kotlin/com/example/vehiclerecognition/domain/logic/VehicleMatcherTest.kt`
- **Added Tests**:
  - Different formatting with same digits: `"12-345-67"` matches `"123-4567"`
  - Ignoring dashes and spaces: `"12-345-67"` matches `"12 345 67"`
  - Numeric-only format: `"12-345-67"` matches `"1234567"`
  - Different digits still fail: `"12-345-67"` doesn't match `"12-345-68"`
  - Invalid digit count fails: plates with less than 7 or more than 8 digits are rejected

## How It Works

### Before (Format-Specific Matching)
```kotlin
// Exact string comparison
detected.licensePlate == entry.licensePlate
// "12-345-67" == "12-345-67" ✓
// "12-345-67" == "1234567"   ✗
```

### After (Numeric-Only Matching)
```kotlin
// Extract digits and compare
extractNumericDigits("12-345-67") == extractNumericDigits("1234567")
// "1234567" == "1234567" ✓
```

## Examples of Matching Behavior

| Watchlist Entry | Detected Plate | Match Result | Reason |
|----------------|----------------|--------------|---------|
| `"12-345-67"` | `"12-345-67"` | ✅ Match | Same digits (1234567) |
| `"12-345-67"` | `"123-4567"` | ✅ Match | Same digits (1234567) |
| `"12-345-67"` | `"12 345 67"` | ✅ Match | Same digits (1234567) |
| `"12-345-67"` | `"1234567"` | ✅ Match | Same digits (1234567) |
| `"12-345-67"` | `"12-345-68"` | ❌ No Match | Different digits (1234567 vs 1234568) |
| `"12-345-67"` | `"12-345"` | ❌ No Match | Invalid digit count (5 digits) |

## Sound Alert Behavior
- **Unchanged**: Sound alerts still trigger when matches are found
- **Enhanced**: Now triggers for any formatting variation with matching digits
- **Example**: Watchlist has `"12-345-67"`, camera detects `"1234567"` → **Sound alert plays**

## Validation Rules
- **Digit Count**: Must have 7-8 digits (Israeli license plate standard)
- **Format Flexibility**: Any formatting is accepted as long as digits match
- **Character Filtering**: Only numeric characters (0-9) are considered for matching

## Testing
All tests pass, including:
- ✅ 15 VehicleMatcher tests (including 6 new numeric-matching tests)
- ✅ All shared module tests
- ✅ Full project build successful 