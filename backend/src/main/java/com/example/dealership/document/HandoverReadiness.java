package com.example.dealership.document;

import java.util.List;

public record HandoverReadiness(
        boolean ready,
        List<DocumentType> missingDocumentTypes,
        List<ReadinessProblem> problems
) {
}
