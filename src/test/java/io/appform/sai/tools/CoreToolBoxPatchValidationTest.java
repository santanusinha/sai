/*
 * Copyright (c) 2025 Original Author(s)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.appform.sai.tools;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Optional;

class CoreToolBoxPatchValidationTest {

    private Method validatePatchFormatMethod;
    private CoreToolBox coreToolBox;

    @BeforeEach
    void setUp() throws Exception {
        coreToolBox = new CoreToolBox(null);
        validatePatchFormatMethod = CoreToolBox.class.getDeclaredMethod("validatePatchFormat", String.class);
        validatePatchFormatMethod.setAccessible(true);
    }

    @Test
    void testEmptyPatch() throws Exception {
        Optional<String> result = validatePatch("");
        assertTrue(result.isPresent());
        assertTrue(result.get().contains("empty"));
    }

    @Test
    void testHunkLineCountMismatch() throws Exception {
        // Hunk says 3 lines but only has 2
        String badPatch = "--- a/test.txt\n"
                + "+++ b/test.txt\n"
                + "@@ -1,3 +1,3 @@\n"
                + " context1\n"
                + "-old line\n"
                + "+new line\n";

        Optional<String> result = validatePatch(badPatch);
        assertTrue(result.isPresent());
        assertTrue(result.get().contains("mismatch") || result.get().contains("count"),
                   "Error should mention line count mismatch: " + result.get());
    }

    @Test
    void testMultipleHunks() throws Exception {
        String multiHunkPatch = "--- a/test.txt\n"
                + "+++ b/test.txt\n"
                + "@@ -1,3 +1,3 @@\n"
                + " context1\n"
                + "-old1\n"
                + "+new1\n"
                + " context2\n"
                + "@@ -10,3 +10,3 @@\n"
                + " context3\n"
                + "-old2\n"
                + "+new2\n"
                + " context4\n";

        Optional<String> result = validatePatch(multiHunkPatch);
        assertTrue(result.isEmpty(), "Valid multi-hunk patch should pass: " + result.orElse(""));
    }

    @Test
    void testNullPatch() throws Exception {
        Optional<String> result = validatePatch(null);
        assertTrue(result.isPresent());
        assertTrue(result.get().contains("empty"));
    }

    @Test
    void testPatchWithCorrectJavaCode() throws Exception {
        // Correct version with leading space on context line
        String goodPatch = "--- a/SaiCommand.java\n"
                + "+++ b/SaiCommand.java\n"
                + "@@ -95,3 +95,3 @@\n"
                + "  @Option(names = {\n"
                + "-        \"--old-option\"\n"
                + "+        \"-i\", \"--input\"\n"
                + "     }, description = \"Execute.\")\n";

        Optional<String> result = validatePatch(goodPatch);
        assertTrue(result.isEmpty(), "Valid Java patch should pass: " + result.orElse(""));
    }

    @Test
    void testPatchWithLineNotStartingWithValidMarker() throws Exception {
        // This tests when a line doesn't start with space, -, or +
        // Note: If a line happens to start with spaces (like Java indentation),
        // it will be treated as a valid context line. The validation can only
        // detect lines that don't start with space, -, or +.
        // Lines starting with letters, brackets, etc. will be caught.
        String badPatch = "--- a/SaiCommand.java\n"
                + "+++ b/SaiCommand.java\n"
                + "@@ -95,3 +95,3 @@\n"
                + " context line\n"
                + "-old line\n"
                + "+new line\n"
                + "missing_leading_space\n";  // This starts with 'm', not space/-/+

        Optional<String> result = validatePatch(badPatch);
        assertTrue(result.isPresent(), "Patch with line not starting with valid marker should fail");
        assertTrue(result.get().contains("malformed"), "Error should mention malformed: " + result.get());
    }

    @Test
    void testPatchWithMissingLeadingSpace() throws Exception {
        // This simulates the exact error: context line without leading space
        String badPatch = "--- a/test.txt\n"
                + "+++ b/test.txt\n"
                + "@@ -1,3 +1,3 @@\n"
                + " context1\n"
                + "-old line\n"
                + "+new line\n"
                + "context2 without leading space\n";

        Optional<String> result = validatePatch(badPatch);
        assertTrue(result.isPresent(), "Patch with missing leading space should fail validation");
        assertTrue(result.get().contains("malformed") || result.get().contains("MUST start with a space"),
                   "Error should mention the formatting issue: " + result.get());
    }

    @Test
    void testPatchWithoutHunkHeader() throws Exception {
        String noHunkPatch = "--- a/test.txt\n"
                + "+++ b/test.txt\n"
                + " some content\n";

        Optional<String> result = validatePatch(noHunkPatch);
        assertTrue(result.isPresent());
        assertTrue(result.get().contains("hunk header"));
    }

    @Test
    void testValidPatch() throws Exception {
        String validPatch = "--- a/test.txt\n"
                + "+++ b/test.txt\n"
                + "@@ -1,3 +1,3 @@\n"
                + " context1\n"
                + "-old line\n"
                + "+new line\n"
                + " context2\n";

        Optional<String> result = validatePatch(validPatch);
        assertTrue(result.isEmpty(), "Valid patch should pass validation: " + result.orElse(""));
    }

    @SuppressWarnings("unchecked")
    private Optional<String> validatePatch(String patchContent) throws Exception {
        return (Optional<String>) validatePatchFormatMethod.invoke(coreToolBox, patchContent);
    }
}
