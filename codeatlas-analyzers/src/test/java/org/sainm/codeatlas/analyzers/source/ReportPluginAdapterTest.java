package org.sainm.codeatlas.analyzers.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sainm.codeatlas.facts.Confidence;
import org.sainm.codeatlas.facts.FactRecord;
import org.sainm.codeatlas.facts.SourceType;

class ReportPluginAdapterTest {

    @TempDir
    Path tempDir;

    // -- Interstage List Creator

    @Test
    void interstageAdapterSupportsIixFiles() throws IOException {
        Path file = tempDir.resolve("report.iix");
        Files.writeString(file, "<form name=\"test\"/>");
        InterstageListCreatorAdapter adapter = new InterstageListCreatorAdapter();
        assertTrue(adapter.supports(file));
    }

    @Test
    void interstageAdapterParsesFieldElements() throws IOException {
        Path file = tempDir.resolve("report.iix");
        Files.writeString(file, """
                <form name="EmployeeReport">
                  <field name="empId" type="string" table="EMPLOYEES" column="EMP_ID"/>
                  <field name="empName" type="string" table="EMPLOYEES" column="EMP_NAME"/>
                  <parameter name="deptFilter" type="string" default="ALL"/>
                </form>""");
        InterstageListCreatorAdapter adapter = new InterstageListCreatorAdapter();
        ReportDefinitionParseResult result = adapter.parse(file);
        assertEquals("EmployeeReport", result.reportName());
        assertEquals(2, result.fields().size());
        assertEquals("empId", result.fields().get(0).fieldName());
        assertEquals("string", result.fields().get(0).dataType());
        assertEquals("EMPLOYEES", result.fields().get(0).sourceTable());
        assertEquals("EMP_ID", result.fields().get(0).sourceColumn());
        assertEquals(1, result.parameters().size());
        assertEquals("deptFilter", result.parameters().get(0).parameterName());
        assertTrue(result.diagnostics().isEmpty());
    }

    @Test
    void interstageAdapterParsesItemElements() throws IOException {
        Path file = tempDir.resolve("report.iix");
        Files.writeString(file, """
                <report name="SalesReport">
                  <item name="amount" type="number" table="SALES" column="AMOUNT"/>
                </report>""");
        InterstageListCreatorAdapter adapter = new InterstageListCreatorAdapter();
        ReportDefinitionParseResult result = adapter.parse(file);
        assertEquals("SalesReport", result.reportName());
        assertEquals(1, result.fields().size());
        assertEquals("amount", result.fields().get(0).fieldName());
    }

    @Test
    void interstageAdapterRecordsDiagnosticOnParseError() throws IOException {
        Path file = tempDir.resolve("report.iix");
        Files.writeString(file, "not valid xml <<<");
        InterstageListCreatorAdapter adapter = new InterstageListCreatorAdapter();
        ReportDefinitionParseResult result = adapter.parse(file);
        assertFalse(result.diagnostics().isEmpty());
        assertTrue(result.diagnostics().get(0).code().equals("INTERSTAGE_PARSE_ERROR"));
    }

    @Test
    void interstageAdapterExtractsReportNameFromTitle() throws IOException {
        Path file = tempDir.resolve("report.iix");
        Files.writeString(file, """
                <form>
                  <title>Monthly Report</title>
                  <field name="col1"/>
                </form>""");
        InterstageListCreatorAdapter adapter = new InterstageListCreatorAdapter();
        ReportDefinitionParseResult result = adapter.parse(file);
        assertEquals("Monthly Report", result.reportName());
    }

    // -- WingArc SVF

    @Test
    void svfAdapterSupportsSvfFiles() throws IOException {
        Path file = tempDir.resolve("form.svf");
        Files.writeString(file, "<svf-form name=\"test\"/>");
        WingArcSvfXmlAdapter adapter = new WingArcSvfXmlAdapter();
        assertTrue(adapter.supports(file));
    }

    @Test
    void svfAdapterParsesFieldsAndParameters() throws IOException {
        Path file = tempDir.resolve("form.svf");
        Files.writeString(file, """
                <svf-form name="InvoiceForm">
                  <form>
                    <field name="invoiceNo" type="string" table="INVOICES" column="INVOICE_NO"/>
                    <field name="amount" type="decimal" table="INVOICES" column="AMOUNT"/>
                  </form>
                  <parameter name="startDate" type="date"/>
                </svf-form>""");
        WingArcSvfXmlAdapter adapter = new WingArcSvfXmlAdapter();
        ReportDefinitionParseResult result = adapter.parse(file);
        assertEquals("InvoiceForm", result.reportName());
        assertEquals(2, result.fields().size());
        assertEquals("invoiceNo", result.fields().get(0).fieldName());
        assertEquals("amount", result.fields().get(1).fieldName());
        assertEquals(1, result.parameters().size());
    }

    // -- BIP

    @Test
    void bipAdapterSupportsXdoFiles() throws IOException {
        Path file = tempDir.resolve("report.xdo");
        Files.writeString(file, "<dataTemplate name=\"test\"/>");
        BipReportAdapter adapter = new BipReportAdapter();
        assertTrue(adapter.supports(file));
    }

    @Test
    void bipAdapterSupportsXdmFiles() throws IOException {
        Path file = tempDir.resolve("model.xdm");
        Files.writeString(file, "<dataModel name=\"test\"/>");
        BipReportAdapter adapter = new BipReportAdapter();
        assertTrue(adapter.supports(file));
    }

    @Test
    void bipAdapterParsesElementsAndParameters() throws IOException {
        Path file = tempDir.resolve("report.xdo");
        Files.writeString(file, """
                <dataTemplate name="OrderReport">
                  <element name="ORDER_ID" dataType="string"/>
                  <element name="AMOUNT" dataType="number"/>
                  <parameter name="status" dataType="string"/>
                </dataTemplate>""");
        BipReportAdapter adapter = new BipReportAdapter();
        ReportDefinitionParseResult result = adapter.parse(file);
        assertEquals("OrderReport", result.reportName());
        assertEquals(2, result.fields().size());
        assertEquals("ORDER_ID", result.fields().get(0).fieldName());
        assertEquals(1, result.parameters().size());
    }

    @Test
    void bipAdapterParsesElementFields() throws IOException {
        Path file = tempDir.resolve("report.xdo");
        Files.writeString(file, """
                <dataTemplate name="Test">
                  <element name="EMP_ID" dataType="string"/>
                  <element name="EMP_NAME" dataType="string"/>
                  <group name="details">
                    <element name="AMOUNT" dataType="number"/>
                  </group>
                </dataTemplate>""");
        BipReportAdapter adapter = new BipReportAdapter();
        ReportDefinitionParseResult result = adapter.parse(file);
        assertEquals(3, result.fields().size());
        assertTrue(result.fields().stream().anyMatch(f -> f.fieldName().equals("EMP_ID")));
        assertTrue(result.fields().stream().anyMatch(f -> f.fieldName().equals("EMP_NAME")));
        assertTrue(result.fields().stream().anyMatch(f -> f.fieldName().equals("AMOUNT")));
    }

    // -- PSF

    @Test
    void psfAdapterSupportsPsfFiles() throws IOException {
        Path file = tempDir.resolve("form.psf");
        Files.writeString(file, "<psf name=\"test\"/>");
        PsfReportAdapter adapter = new PsfReportAdapter();
        assertTrue(adapter.supports(file));
    }

    @Test
    void psfAdapterParsesXmlContent() throws IOException {
        Path file = tempDir.resolve("form.psf");
        Files.writeString(file, """
                <psf name="PrintForm">
                  <field name="title" type="string"/>
                  <parameter name="copies" type="integer" default="1"/>
                </psf>""");
        PsfReportAdapter adapter = new PsfReportAdapter();
        ReportDefinitionParseResult result = adapter.parse(file);
        assertEquals("PrintForm", result.reportName());
        assertEquals(1, result.fields().size());
        assertEquals(1, result.parameters().size());
    }

    // -- PMD

    @Test
    void pmdAdapterSupportsPmdFiles() throws IOException {
        Path file = tempDir.resolve("media.pmd");
        Files.writeString(file, "<pmd name=\"test\"/>");
        PmdReportAdapter adapter = new PmdReportAdapter();
        assertTrue(adapter.supports(file));
    }

    // -- Layout XML

    @Test
    void layoutXmlAdapterSupportsLayoutFiles() throws IOException {
        Path file = tempDir.resolve("report_layout.xml");
        Files.writeString(file, "<layout/>");
        LayoutXmlReportAdapter adapter = new LayoutXmlReportAdapter();
        assertTrue(adapter.supports(file));
    }

    // -- Field Definition XML

    @Test
    void fieldDefinitionXmlAdapterSupportsFieldFiles() throws IOException {
        Path file = tempDir.resolve("fields.xml");
        Files.writeString(file, "<fields/>");
        FieldDefinitionXmlReportAdapter adapter = new FieldDefinitionXmlReportAdapter();
        assertTrue(adapter.supports(file));
    }

    // -- ReportAnalyzer

    @Test
    void reportAnalyzerDefaultsHasAllAdapters() {
        ReportAnalyzer analyzer = ReportAnalyzer.defaults();
        assertEquals(7, analyzer.adapters().size());
    }

    @Test
    void reportAnalyzerScansDirectoryAndDispatches() throws IOException {
        Files.writeString(tempDir.resolve("form1.iix"), """
                <form name="Report1">
                  <field name="f1"/>
                </form>""");
        Files.writeString(tempDir.resolve("form2.svf"), """
                <svf-form name="Report2">
                  <field name="f2"/>
                </svf-form>""");

        ReportAnalyzer analyzer = ReportAnalyzer.defaults();
        ReportAnalysisResult result = analyzer.analyze(tempDir);
        assertEquals(2, result.parseResults().size());
        assertTrue(result.parseResults().stream().anyMatch(r -> r.reportName().equals("Report1")));
        assertTrue(result.parseResults().stream().anyMatch(r -> r.reportName().equals("Report2")));
    }

    @Test
    void reportAnalyzerParsesSingleFile() throws IOException {
        Path file = tempDir.resolve("report.iix");
        Files.writeString(file, """
                <form name="SingleReport">
                  <field name="col1"/>
                </form>""");
        ReportAnalyzer analyzer = ReportAnalyzer.defaults();
        ReportDefinitionParseResult result = analyzer.parseFile(file);
        assertEquals("SingleReport", result.reportName());
        assertEquals(1, result.fields().size());
    }

    @Test
    void reportAnalyzerReturnsDiagnosticForUnsupportedFile() throws IOException {
        Path file = tempDir.resolve("data.csv");
        Files.writeString(file, "a,b,c\n1,2,3\n");
        ReportAnalyzer analyzer = ReportAnalyzer.defaults();
        ReportDefinitionParseResult result = analyzer.parseFile(file);
        assertEquals(1, result.diagnostics().size());
        assertEquals("REPORT_UNSUPPORTED_FORMAT", result.diagnostics().get(0).code());
    }

    @Test
    void reportAnalyzerBuilderAddsCustomAdapter() throws IOException {
        Path file = tempDir.resolve("custom.rpt");
        Files.writeString(file, "<custom name=\"test\"/>");
        ReportAnalyzer analyzer = ReportAnalyzer.builder()
                .add(new ReportPluginAdapter() {
                    @Override
                    public String formatName() { return "Custom"; }
                    @Override
                    public boolean supports(Path f) { return f.toString().endsWith(".rpt"); }
                    @Override
                    public ReportDefinitionParseResult parse(Path f) {
                        return new ReportDefinitionParseResult("CustomReport",
                                List.of(new ReportFieldDefinition("f1", "", "", "",
                                        new SourceLocation(f.toString(), 1, 0))),
                                List.of(), List.of());
                    }
                })
                .build();
        ReportDefinitionParseResult result = analyzer.parseFile(file);
        assertEquals("CustomReport", result.reportName());
        assertEquals(1, result.fields().size());
    }

    // -- ReportFactMapper

    @Test
    void reportFactMapperCreatesFieldAndColumnFacts() throws IOException {
        Path file = tempDir.resolve("report.iix");
        Files.writeString(file, """
                <form name="TestReport">
                  <field name="empId" type="string" table="EMPLOYEES" column="EMP_ID"/>
                </form>""");
        InterstageListCreatorAdapter adapter = new InterstageListCreatorAdapter();
        ReportDefinitionParseResult parseResult = adapter.parse(file);

        ReportFactContext context = new ReportFactContext(
                "proj1", "module1", "src/main/resources", "snap1",
                "run1", "scopeRun1", "src/report.iix");
        JavaSourceFactBatch batch = ReportFactMapper.defaults().map(parseResult, adapter, context);

        assertNotNull(batch);
        assertFalse(batch.facts().isEmpty());
        // Should contain: CONTAINS (ReportDef→ReportField), MAPS_TO_COLUMN (ReportField→DbColumn),
        // READS_TABLE (ReportDef→DbTable), READS_COLUMN (ReportDef→DbColumn)
        assertTrue(batch.facts().size() >= 4,
                "Expected at least 4 facts, got " + batch.facts().size());
        assertTrue(batch.facts().stream()
                .anyMatch(f -> f.relationType().name().equals("CONTAINS")));
        assertTrue(batch.facts().stream()
                .anyMatch(f -> f.relationType().name().equals("MAPS_TO_COLUMN")));
        assertTrue(batch.facts().stream()
                .anyMatch(f -> f.relationType().name().equals("READS_TABLE")));
        assertTrue(batch.facts().stream()
                .anyMatch(f -> f.relationType().name().equals("READS_COLUMN")));
        assertFalse(batch.evidence().isEmpty());
    }

    @Test
    void reportFactMapperHandlesFieldWithoutTableBinding() throws IOException {
        Path file = tempDir.resolve("report.iix");
        Files.writeString(file, """
                <form name="SimpleReport">
                  <field name="title" type="string"/>
                </form>""");
        InterstageListCreatorAdapter adapter = new InterstageListCreatorAdapter();
        ReportDefinitionParseResult parseResult = adapter.parse(file);

        ReportFactContext context = new ReportFactContext(
                "proj1", "module1", "src", "snap1",
                "run1", "scopeRun1", "src/report.iix");
        JavaSourceFactBatch batch = ReportFactMapper.defaults().map(parseResult, adapter, context);

        // Only CONTAINS fact, no MAPS_TO_COLUMN/READS_TABLE/READS_COLUMN
        assertEquals(1, batch.facts().size());
        assertEquals("CONTAINS", batch.facts().getFirst().relationType().name());
    }

    @Test
    void reportFactMapperIncludesParameters() throws IOException {
        Path file = tempDir.resolve("report.iix");
        Files.writeString(file, """
                <form name="ParamReport">
                  <parameter name="dept" type="string" default="ALL"/>
                  <parameter name="year" type="integer"/>
                </form>""");
        InterstageListCreatorAdapter adapter = new InterstageListCreatorAdapter();
        ReportDefinitionParseResult parseResult = adapter.parse(file);

        ReportFactContext context = new ReportFactContext(
                "proj1", "module1", "src", "snap1",
                "run1", "scopeRun1", "src/report.iix");
        JavaSourceFactBatch batch = ReportFactMapper.defaults().map(parseResult, adapter, context);

        assertEquals(2, batch.facts().size());
        assertTrue(batch.facts().stream()
                .allMatch(f -> f.relationType().name().equals("CONTAINS")));
    }

    @Test
    void reportFactMapperProducesCorrectIdentityIds() throws IOException {
        Path file = tempDir.resolve("report.iix");
        Files.writeString(file, """
                <form name="EmployeeReport">
                  <field name="empId" type="string"/>
                </form>""");
        InterstageListCreatorAdapter adapter = new InterstageListCreatorAdapter();
        ReportDefinitionParseResult parseResult = adapter.parse(file);

        ReportFactContext context = new ReportFactContext(
                "proj1", "module1", "src", "snap1",
                "run1", "scopeRun1", "src/report.iix");
        JavaSourceFactBatch batch = ReportFactMapper.defaults().map(parseResult, adapter, context);

        FactRecord fact = batch.facts().getFirst();
        assertTrue(fact.sourceIdentityId().contains("report-definition://"),
                "source ID should be a report-definition URI");
        assertTrue(fact.targetIdentityId().contains("report-field://"),
                "target ID should be a report-field URI");
        assertTrue(fact.targetIdentityId().contains("empId"));
        assertEquals(Confidence.POSSIBLE, fact.confidence());
        assertEquals(SourceType.XML, fact.sourceType());
    }
}
