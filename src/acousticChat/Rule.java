package acousticChat;

public class Rule {
	private int weighting;
	private String match;
	
	public Rule(int weight, String matches) {
		this.weighting = weight;
		this.match = matches;
	}

	public int getWeight() {return this.weighting;}
	public String getMatch() {return this.match;}
}
