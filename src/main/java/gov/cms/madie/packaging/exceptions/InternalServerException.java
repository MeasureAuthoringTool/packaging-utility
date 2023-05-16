package gov.cms.madie.packaging.exceptions;

public class InternalServerException extends RuntimeException {

  private static final long serialVersionUID = -4976407884786223809L;

  public InternalServerException(Exception ex) {
    super(ex);
  }
}
