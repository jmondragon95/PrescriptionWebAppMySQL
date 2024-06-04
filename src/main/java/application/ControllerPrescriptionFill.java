package application;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.sql.Statement;
import java.text.DecimalFormat;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import view.*;

@Controller
public class ControllerPrescriptionFill {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	/*
	 * Patient requests form to fill prescription.
	 */
	@GetMapping("/prescription/fill")
	public String getfillForm(Model model) {
		model.addAttribute("prescription", new PrescriptionView());
		return "prescription_fill";
	}

	// process data from prescription_fill form
	@PostMapping("/prescription/fill")
	public String processFillForm(PrescriptionView prescription, Model model) {

		try (Connection dbConnection = getConnection()){
			/*
			 * valid pharmacy name and address, get pharmacy id and phone
			 */

			//	Search for pharmacy in database by name and address
			PreparedStatement validatePharmacy = dbConnection.prepareStatement
					("select * from pharmacy where name = ? and address = ?");

			validatePharmacy.setString(1, prescription.getPharmacyName());
			validatePharmacy.setString(2, prescription.getPharmacyAddress());

			ResultSet pharmacyTable = validatePharmacy.executeQuery();

			//	If there are no rows in the result table, then pharmacy was not found
			//	Display "Pharmacy not found"
			//	Return user to prescription fill page
			if (!pharmacyTable.next()){
				model.addAttribute("message", "Pharmacy not found");
				model.addAttribute("prescription", prescription);
				return "prescription_fill";
			}

			//	Set pharmacy ID for prescription view model using pharmacy result table.
			prescription.setPharmacyID(pharmacyTable.getInt(1));

			//	Store phone number from database
			String phoneNumberNoHyphens = pharmacyTable.getString(4);

			//	We will format the phone number from the database using string builder and a for loop
			//	Original: 1234567890	New: (123) 456-7890
			StringBuilder phoneNumberWithHyphenAndParentheses = new StringBuilder();

			for (int i = 0; i < phoneNumberNoHyphens.length(); i++){
				if (i == 0) {
					phoneNumberWithHyphenAndParentheses.append("(");
				}
				if (i == 3){
					phoneNumberWithHyphenAndParentheses.append(")");
					phoneNumberWithHyphenAndParentheses.append(" ");
					continue;
				}
				if (i == 6) {
					phoneNumberWithHyphenAndParentheses.append("-");
					continue;
				}
				phoneNumberWithHyphenAndParentheses.append(phoneNumberNoHyphens.charAt(i));
			}

			prescription.setPharmacyPhone(String.valueOf(phoneNumberWithHyphenAndParentheses));

			//	Prepares statement that searches for patient by last name
			PreparedStatement getPatientInfo = dbConnection.prepareStatement
					("select * from patient where last_name = ?");

			getPatientInfo.setString(1, prescription.getPatientLastName());

			ResultSet patientInfoTable = getPatientInfo.executeQuery();

			//	If there are no rows in the result table, then patient was not found
			//	Display "Patient not found"
			//	Return user to prescription fill page
			if (!patientInfoTable.next()){
				model.addAttribute("message", "Patient not found");
				model.addAttribute("prescription", prescription);
				return "prescription_fill";
			}

			//	Set prescription's patient ID and first name using patientInfoTable (result table)
			//	Patient's last name was already entered by user when submitting form
			prescription.setPatient_id(patientInfoTable.getInt(1));
			prescription.setPatientFirstName(patientInfoTable.getString(4));

			//	Prepare statement to retrieve prescription information
			PreparedStatement getPrescriptionInfo = dbConnection.prepareStatement
					("select * from prescription where rx_id = ?");

			getPrescriptionInfo.setInt(1, prescription.getRxid());

			ResultSet prescriptionInfoTable = getPrescriptionInfo.executeQuery();

			//	If there are no rows in the result table, then prescription was not found
			//	Display "Prescription not found"
			//	Return user to prescription fill page
			if (!prescriptionInfoTable.next()){
				model.addAttribute("message", "Prescription not found");
				model.addAttribute("prescription", prescription);
				return "prescription_fill";
			}

			//	Set prescription quantity and refill fields based on data from prescriptionInfoTable
			prescription.setQuantity(prescriptionInfoTable.getInt(5));
			prescription.setRefills(prescriptionInfoTable.getInt(6));

			//	Find drug name using drug id from prescriptionInfoTable
			PreparedStatement getDrugName = dbConnection.prepareStatement
					("select drug_name from drug where drug_id = ?");

			getDrugName.setInt(1, prescriptionInfoTable.getInt(4));

			ResultSet drugTable = getDrugName.executeQuery();

			drugTable.next();

			//	Set prescription's drug name field
			prescription.setDrugName(drugTable.getString(1));

			/*
			 * have we exceeded the number of allowed refills
			 * the first fill is not considered a refill.
			 */

			//	If refills == 0, then we have no more refills remaining.
			//	Display message "No more refills available"
			//	Return user to prescription fill page
//			if (prescription.getRefills() == 0){
//
//			}

			//	Find number of prescription_fills in table
			//	Use this to increment fill_number in database
			PreparedStatement findRefillNumber = dbConnection.prepareStatement
					("select count(*) from prescription_fill where rx_id = ?");

			findRefillNumber.setInt(1, prescription.getRxid());

			ResultSet prescriptionFillTable = findRefillNumber.executeQuery();

			prescriptionFillTable.next();

			//	If there are rows in prescriptionFillTable, then decrement refills by 1
			//	Set as refills remaining
			if (prescriptionFillTable.getInt(1)== prescription.getRefills() + 1){
				model.addAttribute("message", "No more refills available");
				model.addAttribute("prescription", prescription);
				return "prescription_fill";
			}
			if (prescriptionFillTable.getInt(1) == 0){
				prescription.setRefillsRemaining(prescription.getRefills());
			} else {
				prescription.setRefillsRemaining(prescription.getRefills() - 1);
				prescription.setRefills(prescription.getRefillsRemaining());
			}

			//	Increment fillNumber, will be used later when inserting row into prescription_fill
			int fillNumber = prescriptionFillTable.getInt(1) + 1;

			/*
			 * get doctor information
			 */

			//	Get doctor ID from prescriptionInfoTable we retrieved earlier
			int doctorID = prescriptionInfoTable.getInt(3);

			//	Set prescription's doctor ID
			prescription.setDoctor_id(doctorID);

			//	Prepare SQL statement to retrieve last and first name from doctor
			//	Search by ID
			PreparedStatement getDoctorInformation = dbConnection.prepareStatement
					("select last_name, first_name from doctor where id = ?");

			getDoctorInformation.setInt(1, doctorID);

			ResultSet doctorTable = getDoctorInformation.executeQuery();

			//	Sets cursor to first and only row
			doctorTable.next();

			//	Sets prescription's doctor's first and last name using doctorTable
			prescription.setDoctorLastName(doctorTable.getString(1));
			prescription.setDoctorFirstName(doctorTable.getString(2));

			/*
			 * calculate cost of prescription
			 */

			//	Retrieve price and unit amount from drug cost table
			//	Search by drug ID and pharmacy ID
			PreparedStatement getDrugCost = dbConnection.prepareStatement
					("select price, unit_amount from drug_cost where drug_id = ? and pharmacy_id = ?");

			getDrugCost.setInt(1, prescriptionInfoTable.getInt(4));
			getDrugCost.setInt(2, prescription.getPharmacyID());

			ResultSet drugCost = getDrugCost.executeQuery();

			//	If there are no rows in drug cost, then drug cost is not found and subsequently price
			//	cannot be calculated for current refill
			//	Display "Drug cost not found"
			//	Return user to prescription fill page
			if (!drugCost.next()){
				model.addAttribute("message", "Drug cost not found");
				model.addAttribute("prescription", prescription);
				return "prescription_fill";
			}

			//	Format so price will remain at two decimals which takes into consideration the in
			DecimalFormat twoDecimalPlaces = new DecimalFormat("0.00");

			//	Set-up and calculation for drug sale price takes place here
			double drugPrice = drugCost.getDouble(1);
			int unitAmount = drugCost.getInt(2);
			double cost = (drugPrice / unitAmount) * prescription.getQuantity();
			prescription.setCost(twoDecimalPlaces.format(cost));

			//	Set prescription date
			prescription.setDateFilled(LocalDate.now().toString());

			//	Update prescription, decrement refills
//			PreparedStatement updatePrescription = dbConnection.prepareStatement
//					("update prescription set refills = ? where rx_id = ?");
//
//			updatePrescription.setInt(1, prescription.getRefillsRemaining());
//			updatePrescription.setInt(2, prescription.getRxid());
//
//			updatePrescription.executeUpdate();

			//	Insert prescriptionFill, uses SQL function curdate() to set date
			PreparedStatement insertPrescriptionFill = dbConnection.prepareStatement
					("insert into prescription_fill(rx_id, pharmacy_id, date, actual_price, fill_number) " +
									"values(?, ?, curdate(), ?, ?)");

			insertPrescriptionFill.setInt(1, prescription.getRxid());
			insertPrescriptionFill.setInt(2, prescription.getPharmacyID());
			insertPrescriptionFill.setDouble(3, cost);
			insertPrescriptionFill.setInt(4, fillNumber);

			insertPrescriptionFill.executeUpdate();

			// show the updated prescription with the most recent fill information
			model.addAttribute("message", "Prescription filled.");
			model.addAttribute("prescription", prescription);
			return "prescription_show";

		} catch (SQLException e) {
			model.addAttribute("message", "SQL Error: " + e.getMessage());
			model.addAttribute("prescription", prescription);
			return "prescription_fill";
		}
	}
	
	private Connection getConnection() throws SQLException {
		Connection conn = jdbcTemplate.getDataSource().getConnection();
		return conn;
	}

}