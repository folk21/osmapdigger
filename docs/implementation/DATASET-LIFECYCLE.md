---
type: Implementation
title: Dataset loading diagnostics
description: Current Desktop dataset loading lifecycle and diagnostic expectations.
---
# Dataset loading diagnostics

Desktop startup logs the complete dataset loading lifecycle:

- configuration discovery;
- package path resolution;
- package existence and size;
- installation start/end;
- installation exceptions.

Critical startup failures must not be silently hidden behind an empty dataset state.
