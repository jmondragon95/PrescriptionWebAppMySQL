package application;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import view.*;

@Controller
public class ControllerPrescriptionCreate {

	@Autowired
	private JdbcTemplate jdbcTemplate;
	
	/*
	 * Doctor requests blank form for new prescription.
	 */
	@GetMapping("/prescription/new")
	public String getPrescriptionForm(Model model) {
		model.addAttribute("prescription", new PrescriptionView());
		return "prescription_create";
	}

	// process data entered on prescription_create form
	@PostMapping("/prescription")
	public String createPrescription(PrescriptionView prescription, Model model) {

		System.out.println("createPrescription " + prescription);

		try (Connection dbConnection = getConnection()){
			/*
			 * valid doctor name and id
			 */

			PreparedStatement validateDoctorNameAndID = dbConnection.prepareStatement
					("select * from doctor where id = ? and first_name = ? and last_name = ?");

			validateDoctorNameAndID.setInt(1, prescription.getDoctor_id());
			validateDoctorNameAndID.setString(2, prescription.getDoctorFirstName());
			validateDoctorNameAndID.setString(3, prescription.getDoctorLastName());

			ResultSet doctorTable = validateDoctorNameAndID.executeQuery();

			if (!doctorTable.next()){
				model.addAttribute("message", "Doctor not found");
				model.addAttribute("prescription", prescription);
				return "prescription_create";
			}

			/*
			 * valid patient name and id
			 */

			PreparedStatement validatePatientNameAndID = dbConnection.prepareStatement
					("select * from patient where id = ? and first_name = ? and last_name = ?");

			validatePatientNameAndID.setInt(1, prescription.getPatient_id());
			validatePatientNameAndID.setString(2, prescription.getPatientFirstName());
			validatePatientNameAndID.setString(3, prescription.getPatientLastName());

			ResultSet patientTable = validatePatientNameAndID.executeQuery();

			if (!patientTable.next()){
				model.addAttribute("message", "Patient not found");
				model.addAttribute("prescription", prescription);
				return "prescription_create";
			}

			/*
			 * valid drug name
			 */

			PreparedStatement validateDrugName = dbConnection.prepareStatement
					("select * from drug where drug_name = ?");

			validateDrugName.setString(1, prescription.getDrugName());

			ResultSet drugTable = validateDrugName.executeQuery();

			if (!drugTable.next()){
				model.addAttribute("message", "Drug not found");
				model.addAttribute("prescription", prescription);
				return "prescription_create";
			}

			/*
			 * insert prescription
			 */

			PreparedStatement insertPrescription = dbConnection.prepareStatement
					("insert into prescription(patient_id, doctor_id, drug_id, quantity, refills) " +
							"values(?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);

			insertPrescription.setInt(1, prescription.getPatient_id());
			insertPrescription.setInt(2, prescription.getDoctor_id());
			insertPrescription.setInt(3, drugTable.getInt(1));
			insertPrescription.setInt(4, prescription.getQuantity());
			insertPrescription.setInt(5, prescription.getRefills());

			insertPrescription.executeUpdate();

			ResultSet prescriptionGeneratedKey = insertPrescription.getGeneratedKeys();

			if (prescriptionGeneratedKey.next()){
				prescription.setRxid(prescriptionGeneratedKey.getInt(1));
			}

			model.addAttribute("message", "Prescription created.");
			model.addAttribute("prescription", prescription);
			return "prescription_show";

		} catch (SQLException e) {
			model.addAttribute("message", "SQL Error: " + e.getMessage());
			model.addAttribute("prescription", prescription);
			return "prescription_create";
		}
	}
	
	private Connection getConnection() throws SQLException {
		Connection conn = jdbcTemplate.getDataSource().getConnection();
		return conn;
	}

}
