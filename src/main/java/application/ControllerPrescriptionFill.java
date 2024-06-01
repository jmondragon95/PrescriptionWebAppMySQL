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
			// TODO validate pharmacy name/address

			PreparedStatement validatePharmacy = dbConnection.prepareStatement
					("select * from pharmacy where name = ? and address = ?");

			validatePharmacy.setString(1, prescription.getPharmacyName());
			validatePharmacy.setString(2, prescription.getPharmacyAddress());

			ResultSet pharmacyTable = validatePharmacy.executeQuery();

			if (!pharmacyTable.next()){
				model.addAttribute("message", "Pharmacy not found");
				model.addAttribute("prescription", prescription);
				return "prescription_fill";
			}

			// TODO get pharmacy id and phone

			prescription.setPharmacyID(pharmacyTable.getInt(1));

			String phoneNumberNoHyphens = pharmacyTable.getString(4);
			StringBuilder phoneNumberWithHyphens = new StringBuilder();

			for (int i = 0; i < phoneNumberNoHyphens.length(); i++){
				if (i == 0) {
					phoneNumberWithHyphens.append("(");
				}
				if (i == 3){
					phoneNumberWithHyphens.append(")");
					phoneNumberWithHyphens.append(" ");
					continue;
				}
				if (i == 6) {
					phoneNumberWithHyphens.append("-");
					continue;
				}
				phoneNumberWithHyphens.append(phoneNumberNoHyphens.charAt(i));
			}

			prescription.setPharmacyPhone(String.valueOf(phoneNumberWithHyphens));

			// TODO find the patient information

			PreparedStatement getPatientInfo = dbConnection.prepareStatement
					("select * from patient where last_name = ?");

			getPatientInfo.setString(1, prescription.getPatientLastName());

			ResultSet patientInfoTable = getPatientInfo.executeQuery();

			if (!patientInfoTable.next()){
				model.addAttribute("message", "Patient not found");
				model.addAttribute("prescription", prescription);
				return "prescription_fill";
			}


			prescription.setPatient_id(patientInfoTable.getInt(1));
			prescription.setPatientFirstName(patientInfoTable.getString(4));

			// TODO find the prescription

			PreparedStatement getPrescriptionInfo = dbConnection.prepareStatement
					("select * from prescription where rx_id = ?");

			getPrescriptionInfo.setInt(1, prescription.getRxid());

			ResultSet prescriptionInfoTable = getPrescriptionInfo.executeQuery();

			if (!prescriptionInfoTable.next()){
				model.addAttribute("message", "Prescription not found");
				model.addAttribute("prescription", prescription);
				return "prescription_fill";
			}

			prescription.setQuantity(prescriptionInfoTable.getInt(5));
			prescription.setRefills(prescriptionInfoTable.getInt(6));

			//	Find drug name

			PreparedStatement getDrugName = dbConnection.prepareStatement
					("select drug_name from drug where drug_id = ?");

			getDrugName.setInt(1, prescriptionInfoTable.getInt(4));

			ResultSet drugTable =getDrugName.executeQuery();

			drugTable.next();

			prescription.setDrugName(drugTable.getString(1));

			/*
			 * have we exceeded the number of allowed refills
			 * the first fill is not considered a refill.
			 */
			// TODO

			if (prescription.getRefills() == 0){
				model.addAttribute("message", "No more refills available");
				model.addAttribute("prescription", prescription);
				return "prescription_fill";
			}

			//	Calculate remaining refills
			PreparedStatement findRefillNumber = dbConnection.prepareStatement
					("select count(*) from prescription_fill where rx_id = ?");

			findRefillNumber.setInt(1, prescription.getRxid());

			ResultSet prescriptionFillTable = findRefillNumber.executeQuery();

			if (prescriptionFillTable.next()){
				prescription.setRefillsRemaining(prescription.getRefills() - 1);
			}

			int fillNumber = prescriptionFillTable.getInt(1) + 1;

			/*
			 * get doctor information
			 */
			// TODO

			int doctorID = prescriptionInfoTable.getInt(3);

			prescription.setDoctor_id(doctorID);

			PreparedStatement getDoctorInformation = dbConnection.prepareStatement
					("select last_name, first_name from doctor where id = ?");

			getDoctorInformation.setInt(1, doctorID);

			ResultSet doctorTable = getDoctorInformation.executeQuery();

			if (doctorTable.next()){
				prescription.setDoctorLastName(doctorTable.getString(1));
				prescription.setDoctorFirstName(doctorTable.getString(2));
			}

			/*
			 * calculate cost of prescription
			 */
			// TODO

			PreparedStatement getDrugCost = dbConnection.prepareStatement
					("select price, unit_amount from drug_cost where drug_id = ? and pharmacy_id = ?");

			getDrugCost.setInt(1, prescriptionInfoTable.getInt(4));
			getDrugCost.setInt(2, prescription.getPharmacyID());

			ResultSet drugCost = getDrugCost.executeQuery();

			if (!drugCost.next()){
				model.addAttribute("message", "Drug cost not found");
				model.addAttribute("prescription", prescription);
				return "prescription_fill";
			}

			DecimalFormat twoDecimalPlaces = new DecimalFormat("0.00");

			double drugPrice = drugCost.getDouble(1);
			int unitAmount = drugCost.getInt(2);
			double cost = (drugPrice / unitAmount) * prescription.getQuantity();
			prescription.setCost(twoDecimalPlaces.format(cost));

			prescription.setDateFilled(LocalDate.now().toString());

			// TODO save updated prescription

			PreparedStatement updatePrescription = dbConnection.prepareStatement
					("update prescription set refills = ? where rx_id = ?");

			updatePrescription.setInt(1, prescription.getRefillsRemaining());
			updatePrescription.setInt(2, prescription.getRxid());

			updatePrescription.executeUpdate();

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