package org.springframework.samples.petclinic.rest.importcsv;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.samples.petclinic.model.Owner;
import org.springframework.samples.petclinic.model.Pet;
import org.springframework.samples.petclinic.model.PetType;
import org.springframework.samples.petclinic.service.ClinicService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(exposedHeaders = "errors, content-type")
@RequestMapping("api/import")
public class ImportCSV {

    @Autowired
    private ClinicService clinicService;

    @PreAuthorize("hasRole(@roles.OWNER_ADMIN)")
    @RequestMapping(value = "importPets",
        method = RequestMethod.POST,
        consumes = "text/plain",
        produces = "application/json")
    public ResponseEntity<List<Pet>> importPets(@RequestBody String csv) {
        List<Pet> pets = new LinkedList<Pet>();
        for (String line : csv.split("\\r?\\n")) {
            String[] fields = line.split(";");
            // validate the fields - check that we have as many as we need
            // check each field - it should contain proper information
            Pet pet = new Pet();

            // set the name
            pet.setName(fields[0]);


            // set the birthdate
            try {
                pet.setBirthDate((new SimpleDateFormat("yyyy-MM-dd")).parse(fields[1]));
            } catch (ParseException e) {
                HttpHeaders headers = new HttpHeaders();
                headers.add("errors", "date " + field + " not valid");
                return new ResponseEntity<List<Pet>>(headers, HttpStatus.BAD_REQUEST);
            }

            // set the type
            if (pet != null) {
                ArrayList<PetType> ts = (ArrayList<PetType>) clinicService.findPetTypes();
                for (int j = 0; j < ts.size(); j++) {
                    if (ts.get(j).getName().toLowerCase().equals(fields[2])) {
                        pet.setType(ts.get(j));
                        break;
                    }
                }
            }

            // set the owner
            if (pet != null) {
                String owner = fields[3];
                List<Owner> matchingOwners = clinicService.findAllOwners()
                    .stream()
                    .filter(o -> o.getLastName().equals(owner))
                    .collect(Collectors.toList());

                if (matchingOwners.size() == 0) {
                    HttpHeaders headers = new HttpHeaders();
                    headers.add("errors", "Owner not found");
                    return new ResponseEntity<List<Pet>>(headers, HttpStatus.BAD_REQUEST);
                }
                if (matchingOwners.size() > 1) {
                    HttpHeaders headers = new HttpHeaders();
                    headers.add("errors", "Owner not unique");
                    return new ResponseEntity<List<Pet>>(headers, HttpStatus.BAD_REQUEST);
                }
                pet.setOwner(matchingOwners.iterator().next());
            }

            // process the action
            if (fields.length == 5) {

                String field = fields[4];

                if (field.toLowerCase().equals("add")) {
                    clinicService.savePet(pet);
                } else {
                    for (Pet q : pet.getOwner().getPets()) {
                        if (q.getName().equals(pet.getName())) {
                            if (q.getType().getId().equals(pet.getType().getId())) {
                                if (pet.getBirthDate().equals(q.getBirthDate())) {
                                    clinicService.deletePet(q);
                                }
                            }
                        }
                    }
                }
            } else {
                clinicService.savePet(pet);
            }
            pets.add(pet);
        }
        return new ResponseEntity<List<Pet>>(pets, HttpStatus.OK);
    }

    private Boolean setPetBirthDate(Pet pet, String dateString) {
        try {
            pet.setBirthDate((new SimpleDateFormat("yyyy-MM-dd")).parse(dateString));
            return true;
        } catch (ParseException e) {
            // HttpHeaders headers = new HttpHeaders();
            // headers.add("errors", "date " + field + " not valid");
            return false;
        }
    }
}
