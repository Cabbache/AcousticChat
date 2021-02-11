package acousticChat;

public class InsertRule extends Rule {
	public String values;
	
	public InsertRule(int weight, String matches, String values) {
		super(weight, matches);
		this.values = values;
	}
	
	public String getValues() {return this.values;}
}
