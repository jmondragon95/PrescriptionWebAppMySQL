package application;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import view.*;
/*
 * Controller class for patient interactions.
 *   register as a new patient.
 *   update patient profile.
 */
@Controller
public class ControllerPatientUpdate {
	
	@Autowired
	private JdbcTemplate jdbcTemplate;
	
	/*
	 *  Display patient profile for patient id.
	 */
	@GetMapping("/patient/edit/{id}")
	public String getUpdateForm(@PathVariable int id, Model model) {
		PatientView patient = new PatientView();
		patient.setId(id);
		try (Connection dbConnection = getConnection()){
			PreparedStatement searchForPatient = dbConnection.prepareStatement
					("select last_name, first_name, birthdate, street, city, state, zip, doctor_id " +
							 "from patient where id = ?");
			searchForPatient.setInt(1, id);

			ResultSet patientResult = searchForPatient.executeQuery();

			if (patientResult.next()){
				patient.setLast_name(patientResult.getString(1));
				patient.setFirst_name(patientResult.getString(2));
				patient.setBirthdate(patientResult.getString(3));
				patient.setStreet(patientResult.getString(4));
				patient.setCity(patientResult.getString(5));
				patient.setState(patientResult.getString(6));
				patient.setZipcode(patientResult.getString(7));

				int doctorID = patientResult.getInt(8);

				PreparedStatement getPatientsDoctor = dbConnection.prepareStatement
						("select first_name, last_name from doctor where id = ?");


				//	Enter doctor's name that patient entered into prepared SQL statement
				getPatientsDoctor.setInt(1, doctorID);

				//	Execute query and store results in result set "patient's doctor"
				ResultSet patientsDoctor = getPatientsDoctor.executeQuery();

				StringBuilder doctorName = new StringBuilder();
				if (patientsDoctor.next()){
					doctorName.append(patientsDoctor.getString(1));
					doctorName.append(" ");
					doctorName.append(patientsDoctor.getString(2));
				}

				patient.setPrimaryName(String.valueOf(doctorName));

				model.addAttribute("patient", patient);
				return "patient_edit";

			} else {
				model.addAttribute("message", "Patient not found");
				model.addAttribute("patient", patient);
				return "index";
			}

		} catch (SQLException e) {
			model.addAttribute("message", "SQL Error: " + e.getMessage());
			model.addAttribute("patient", patient);
			return "patient_get";
		}
		//  if not found, return to home page using return "index"; 
		//  else create PatientView and add to model.
		// model.addAttribute("message", some message);
		// model.addAttribute("patient", pv
		// return editable form with patient data
}
	
	
	/*
	 * Process changes from patient_edit form
	 *  Primary doctor, street, city, state, zip can be changed
	 *  ssn, patient id, name, birthdate, ssn are read only in template.
	 */
	@PostMapping("/patient/edit")
	public String updatePatient(PatientView patient, Model model) {

		// validate doctor last name 
		
		// TODO
		try (Connection dbConnection = getConnection()){
			PreparedStatement validatePatientsDoctor = dbConnection.prepareStatement
					("select id, first_name, last_name from doctor where last_name = ?");

			//	Remove first name from doctor name since we are looking up by last name only
			String doctorName = patient.getPrimaryName();
			String doctorLastName;

			if (doctorName.contains(" ")){
				String[] doctorNames = doctorName.split(" ");
				doctorLastName = doctorNames[1];
			} else {
				doctorLastName = doctorName;
			}

			//	Enter doctor's name that patient entered into prepared SQL statement
			validatePatientsDoctor.setString(1, doctorLastName);

			//	Execute query and store results in result set "patient's doctor"
			ResultSet patientsDoctor = validatePatientsDoctor.executeQuery();

			if (!patientsDoctor.next()){
				model.addAttribute("message", "Doctor not found");
				model.addAttribute("patient", patient);
				return "patient_edit";
			}

			int doctorID = patientsDoctor.getInt(1);

			//	Set primary name for patient as doctor name retrieved from database
			//	Just in case user got last name correct but not first name
			patient.setPrimaryName
					(patientsDoctor.getString(2) + " " + patientsDoctor.getString(3));

			//	Prepare a statement for inserting a new patient into the patient table
			PreparedStatement updatePatientStatement = dbConnection.prepareStatement
					("update patient set street = ?, city = ?, state = ?, zip = ?, doctor_id = ? " +
							 "where id = ?",
							Statement.RETURN_GENERATED_KEYS);

			//	Enter values from form into placeholders in prepared statement
			updatePatientStatement.setString(1, patient.getStreet());
			updatePatientStatement.setString(2, patient.getCity());
			updatePatientStatement.setString(3, patient.getState());
			updatePatientStatement.setString(4, patient.getZipcode());
			updatePatientStatement.setInt(5, doctorID);
			updatePatientStatement.setInt(6, patient.getId());

			//	Enter new patient into patient table
			updatePatientStatement.executeUpdate();

			//	Retrieve generated key from insert
			ResultSet patientResultSet = updatePatientStatement.getGeneratedKeys();

			if (patientResultSet.next()){
				patient.setId(patientResultSet.getInt(1));
			}

			// display patient data and the generated patient ID,  and success message
			model.addAttribute("message", "Update successful.");
			model.addAttribute("patient", patient);
			return "patient_show";

		} catch (SQLException e) {
			model.addAttribute("message", "SQL Error: " + e.getMessage());
			model.addAttribute("patient", patient);
			return "patient_edit";
		}
	}

	private Connection getConnection() throws SQLException {
		Connection conn = jdbcTemplate.getDataSource().getConnection();
		return conn;
	}
}
