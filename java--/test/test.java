class Fac {
	public int ComputeFac(int num){
		int num_aux;
		if (num < 1)
			num_aux = 1;
		else
			num_aux = num * (this.ComputeFac(num-1));
		return num_aux;
	}
	public static void main(String[] a){
		ioPlus.println(new Fac().ComputeFac(10));
	}
 }