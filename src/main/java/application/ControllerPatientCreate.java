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
import org.springframework.web.bind.annotation.PostMapping;

import view.*;

/*
 * Controller class for patient interactions.
 *   register as a new patient.
 *   update patient profile.
 */
@Controller
public class ControllerPatientCreate {
	
	@Autowired
	private JdbcTemplate jdbcTemplate;
	
	
	/*
	 * Request blank patient registration form.
	 */
	@GetMapping("/patient/new")
	public String getNewPatientForm(Model model) {
		// return blank form for new patient registration
		model.addAttribute("patient", new PatientView());
		return "patient_register";
	}
	
	/*
	 * Process data from the patient_register form
	 */
	@PostMapping("/patient/new")
	public String createPatient(PatientView patient, Model model) {

		try (Connection dbConnection = getConnection()) {

			//	Prepare a statement for looking up the patient's doctor from the doctor table using their
			//	last name
			PreparedStatement validatePatientsDoctor = dbConnection.prepareStatement
					("select * from doctor where last_name = ?");

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
				return "patient_register";
			}

			int doctorID = patientsDoctor.getInt(1);
			//	Set primary name for patient as doctor name retrieved from database
			//	Just in case user got last name correct but not first name
			patient.setPrimaryName
					(patientsDoctor.getString(4) + " " + patientsDoctor.getString(3));

			//	Prepare a statement for inserting a new patient into the patient table
			PreparedStatement insertPatientStatement = dbConnection.prepareStatement
					("insert into patient(last_name, first_name, birthdate, ssn, street, city, state," +
							" zip, doctor_id) values(?, ?, ?, ?, ?, ?, ?, ?, ?)",
							Statement.RETURN_GENERATED_KEYS);

			//	Enter values from form into placeholders in prepared statement
			insertPatientStatement.setString(1, patient.getLast_name());
			insertPatientStatement.setString(2, patient.getFirst_name());
			insertPatientStatement.setString(3, patient.getBirthdate());
			insertPatientStatement.setString(4, patient.getSsn());
			insertPatientStatement.setString(5, patient.getStreet());
			insertPatientStatement.setString(6, patient.getCity());
			insertPatientStatement.setString(7, patient.getState());
			insertPatientStatement.setString(8, patient.getZipcode());
			insertPatientStatement.setInt(9, doctorID);

			//	Enter new patient into patient table
			insertPatientStatement.executeUpdate();

			//	Retrieve generated key from insert
			ResultSet patientResultSet = insertPatientStatement.getGeneratedKeys();
			if (patientResultSet.next()){
				patient.setId(patientResultSet.getInt(1));
			}

			// display patient data and the generated patient ID,  and success message
			model.addAttribute("message", "Registration successful.");
			model.addAttribute("patient", patient);
			return "patient_show";

		} catch (SQLException e) {
			model.addAttribute("message", "SQL Error." + e.getMessage());
			model.addAttribute("patient", patient);
			return "patient_register";
		}
	}
	
	/*
	 * Request blank form to search for patient by id and name
	 */
	@GetMapping("/patient/edit")
	public String getSearchForm(Model model) {
		model.addAttribute("patient", new PatientView());
		return "patient_get";
	}
	
	/*
	 * Perform search for patient by patient id and name.
	 */
	@PostMapping("/patient/show")
	public String showPatient(PatientView patient, Model model) {

		try (Connection dbConnection = getConnection()){
			//	Prepare SQL statement for searching patient table based on patient ID and last name
			PreparedStatement searchForPatient = dbConnection.prepareStatement
					("select last_name, first_name, zip, birthdate, street, city, state, doctor_id "
							+ "from patient where id = ? and last_name = ?");
			searchForPatient.setInt(1, patient.getId());
			searchForPatient.setString(2, patient.getLast_name());

			ResultSet patientResult = searchForPatient.executeQuery();
			if (patientResult.next()){
				patient.setLast_name(patientResult.getString(1));
				patient.setFirst_name(patientResult.getString(2));
				patient.setZipcode(patientResult.getString(3));
				patient.setBirthdate(patientResult.getString(4));
				patient.setStreet(patientResult.getString(5));
				patient.setCity(patientResult.getString(6));
				patient.setState(patientResult.getString(7));

				PreparedStatement searchForDoctor = dbConnection.prepareStatement
						("select first_name, last_name from doctor where id = ?");

				searchForDoctor.setInt(1, patientResult.getInt(8));

				ResultSet doctorResult = searchForDoctor.executeQuery();

				StringBuilder doctorName = new StringBuilder();

				if (doctorResult.next()){
					doctorName.append(doctorResult.getString(1));
					doctorName.append(" ");
					doctorName.append(doctorResult.getString(2));
				} else {
					model.addAttribute("message", "Doctor not found");
					model.addAttribute("patient", patient);
					return "patient_get";
				}

				patient.setPrimaryName(String.valueOf(doctorName));

				model.addAttribute("patient", patient);
				System.out.println("end getPatient " + patient);
				return "patient_show";

			} else {
				model.addAttribute("message", "Patient not found");
				model.addAttribute("patient", patient);
				return "patient_get";
			}

		} catch (SQLException e) {
			System.out.println("SQL Error in getPatient " + e.getMessage());
			model.addAttribute("message", "SQL Error: " + e.getMessage());
			model.addAttribute("patient", patient);
			return "patient_get";
		}
	}
	
	/*
	 * return JDBC Connection using jdbcTemplate in Spring Server
	 */

	private Connection getConnection() throws SQLException {
		Connection conn = jdbcTemplate.getDataSource().getConnection();
		return conn;
	}
}
