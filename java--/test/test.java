class Fac {
	int a;

	public int ComputeFac(int num){
		int num_aux;
		if (num_aux < 1) {
			io.println(123456);
			num_aux = 1;
		}
		else {
			num_aux = num * (this.ComputeFac(num-1));
		}
		return num_aux;
	}
	public static void main(String[] a){
		new Fac().ComputeFac(10);
		io.println(new Fac().ComputeFac(10));
	}
 }
