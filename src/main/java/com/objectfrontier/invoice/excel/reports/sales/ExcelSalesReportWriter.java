package com.objectfrontier.invoice.excel.reports.sales;

import com.objectfrontier.invoice.excel.exception.ReportException;
import com.objectfrontier.invoice.excel.system.InvoiceUtil.MONTH;
import com.objectfrontier.invoice.excel.system.Utils;
import com.objectfrontier.job.Task;
import com.objectfrontier.model.ClientAccount;
import com.objectfrontier.model.Employee;
import com.objectfrontier.model.Project;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.SortedMap;
import java.util.logging.Logger;

/**
 * Created by ahariharan on 6/19/15.
 */
public class ExcelSalesReportWriter {

  private Logger log = Logger.getLogger(this.getClass().getSimpleName());

  private SortedMap<String, ClientAccount> clientAccounts;
  private XSSFWorkbook workbook;
  private XSSFSheet sheet;
  private XSSFSheet summarySheet;

  private int reportingYear;
  private MONTH reportingMonth;
  int currentRowNum;
  int currentColumnNum;

  private ClientAccount currentClientAccount;
  private Project currentProject;
  private Employee currentEmployee;

  private String[] columns = new String[] { "Sl No", "First Name", "Last Name", "Role", "Work Location", "Client Name",
                  "Project Code", "Invoice Rate /location /month", "Business Days Worked", "Invoiced Amount",
                  "Shadow Resource", "Business Start Date of Month", "Business End Date of Month" };

  private Utils utils;
  private Task task;

  public ExcelSalesReportWriter(SortedMap<String, ClientAccount> clientAccounts, Task task) {
    utils = Utils.getInstance();
    log.removeHandler(utils.getHandler());
    log.addHandler(utils.getHandler());
    this.clientAccounts = clientAccounts;
    this.task = task;
  }

  private void init(XSSFWorkbook existingWorkbook) throws ReportException {
    if (clientAccounts == null || clientAccounts.isEmpty())
      throw new ReportException("No data available to generate report");

    this.workbook = (existingWorkbook == null) ? new XSSFWorkbook() : existingWorkbook;
    sheet = createSheet(getReportingMonthSheetName());
    summarySheet = createSheet(getReportingMonthSheetName() + " - Invoice Summary");
    writeHeader();
    log("Initializing workbook completed before writing report");

  }

  private String getReportingMonthSheetName() {
    return reportingMonth.toString() + reportingYear;
  }

  private XSSFSheet createSheet(String sheetName) {
    XSSFSheet sheet = null;

    sheet = this.workbook.getSheet(sheetName);
    if (sheet != null) {
      this.workbook.removeSheetAt(workbook.getSheetIndex(sheet));
    }
    sheet = this.workbook.createSheet(sheetName);

    return sheet;
  }

  private void writeHeader() {

    XSSFRow row = sheet.createRow(thisRow());
    for(String columnName : columns) {
      XSSFCell cell = row.createCell(thisColumn()) ;
      cell.setCellValue(columnName);
    }
  }

//  private XSSFFont headerFont() {
//    XSSFCellStyle cellStyle = workbook.createCellStyle();
//    cellStyle.setAlignment(XSSFCellStyle.ALIGN_CENTER);
//    XSSFFont font = workbook.createFont();
//    font.setBoldweight(XSSFFont.BOLDWEIGHT_BOLD);
//    return font;
//  }

  private int thisRow() {
    return currentRowNum++;
  }

  private int thisColumn() {
    if (currentColumnNum + 1   == columns.length) {
      currentColumnNum = 0;
      return columns.length - 1;
    }
    return currentColumnNum++;
  }

  public XSSFWorkbook getSalesReport(XSSFWorkbook workbook, int year, MONTH month) throws ReportException {
    this.reportingYear = year;
    this.reportingMonth = month;
    log("Preparing to write report to target excel");
    try {
      init(workbook);
    } catch (ReportException e){
      log(String.format("Initialization failed, skipping report generation for %4d,%s", year, month.toString()));
      return workbook;
    }

    XSSFRow summaryRow = summarySheet.createRow(summarySheet.getLastRowNum() + 1);
    summaryRow.createCell(0).setCellValue("Client Name");
    summaryRow.createCell(1).setCellValue("Project/SOW Code");
    summaryRow.createCell(2).setCellValue("Total Invoice Amount/Project");
    summaryRow.createCell(3).setCellValue("Total Invoice Amount/Client");

    for (String s : clientAccounts.keySet()) {
      currentClientAccount = clientAccounts.get(s);
      writeClientSales();
    }

    for (int x = 0; x < columns.length; x++) {
      sheet.autoSizeColumn(x);
      if (x < 4) summarySheet.autoSizeColumn(x);
    }


    return this.workbook;
  }

  private void writeClientSales() throws ReportException {
    if (currentClientAccount == null)
      throw new ReportException("Missing client account data");
    log(String.format("Started writing report for client %s", currentClientAccount.name));
    log(currentClientAccount);
    XSSFRow summaryRow = summarySheet.createRow(summarySheet.getLastRowNum() + 1);
    summaryRow.createCell(0).setCellValue(currentClientAccount.name);

    for (Project project : currentClientAccount.projects) {
      this.currentProject = project;
      log(String.format("Processing report for project %s[%s]", project.name, project.code));
      writeProjectSales();
      log("Adding project to summary tab");
      log(String.format("Project: %s, Code: %s, %.2f", project.name, project.code, project.getTotalInvoiceAmount()));
      summaryRow.createCell(1).setCellValue(String.format("%s",  project.code));
      summaryRow.createCell(2).setCellValue(currentProject.getTotalInvoiceAmount());
      summaryRow = summarySheet.createRow(summarySheet.getLastRowNum() + 1);
    }
    summaryRow.createCell(3).setCellValue(currentClientAccount.getTotalInvoiceAmount());
    summarySheet.createRow(summarySheet.getLastRowNum() + 1);
    log(String.format("Client: %s, Code: %s, %.2f", currentClientAccount.name, currentClientAccount.code, currentClientAccount.getTotalInvoiceAmount()));
    log(String.format("Finished writing report for client %s", currentClientAccount.name));
  }

  private void writeProjectSales() {
    for(Employee employee : currentProject.employees) {
      this.currentEmployee = employee;
      writeDetails();
    }
  }

  private void writeDetails() {
    log(String.format("Started writing details for employee: %s %s", currentEmployee.firstName, currentEmployee.lastName));
    XSSFRow row = sheet.createRow(thisRow());
    XSSFCell startDateCell;
    XSSFCell endDateCell;
    XSSFCell billableDaysCell;
    createCell(row).setCellValue(currentRowNum-1);
    createCell(row).setCellValue(currentEmployee.firstName);
    createCell(row).setCellValue(currentEmployee.lastName);
    createCell(row).setCellValue(currentEmployee.role);
    createCell(row).setCellValue(currentEmployee.location);
    createCell(row).setCellValue(currentClientAccount.name);
    createCell(row).setCellValue(currentProject.code);
    createCell(row).setCellValue(currentEmployee.billing.rate);

    billableDaysCell = createCell(row);
    billableDaysCell.setCellValue(currentEmployee.billing.billableDays);

    createCell(row).setCellValue(currentEmployee.billing.billed);
    createCell(row).setCellValue(currentEmployee.shadow ? "Yes" : "No");

    startDateCell = createCell(row);
    startDateCell.setCellValue(currentEmployee.billing.startDate);
    startDateCell.setCellStyle(getDateFormatStyle());

    endDateCell = createCell(row);
    endDateCell.setCellValue(currentEmployee.billing.endDate);
    endDateCell.setCellStyle(getDateFormatStyle());

    if(currentEmployee.shadow) {
      billableDaysCell.setCellFormula(
                      String.format("NETWORKDAYS(%s,%s)-%2f", startDateCell.getReference(), endDateCell.getReference(),
                                      currentEmployee.billing.ptoDays));
    }
    log(String.format("Finished writing deatils for employee: %s %s", currentEmployee.firstName, currentEmployee.lastName));
    task.done();
  }

  private CellStyle getDateFormatStyle() {
    CellStyle cellStyle = workbook.createCellStyle();
    CreationHelper createHelper = workbook.getCreationHelper();
    cellStyle.setDataFormat(
                    createHelper.createDataFormat().getFormat("dd-mmm-yyyy"));
    return cellStyle;
  }

  private XSSFCell createCell(XSSFRow row) {
    return row.createCell(thisColumn());
  }

  private void log(Object content) {
    String prefix = currentClientAccount == null ? "" : currentClientAccount.name + ": ";
    prefix = prefix + getReportingMonthSheetName() + ": ";
    if (content instanceof Exception) {
      content = "\nERROR: " + utils.getStackTrace((Exception)content);
    }
    log.info(prefix + content);
  }
}
