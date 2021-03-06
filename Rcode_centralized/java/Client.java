import java.net.*;
import java.io.*;
import java.util.*;
import java.lang.*;
import Jama.Matrix;
//cox client
class Client {
	public static void main(String args[]) {
		try {
			String file_name;
			FileInputStream file_stream;
			DataInputStream file_in;
			BufferedReader file_br;
			String file_line;
			String[] line_tokens;
			
			Vector<Double> ZTDrow;
			Vector< Vector<Double> > ZTDv = new Vector< Vector<Double> >();
			double[][] ZTDa;
			double[] DIa, Tuniq;
			double[][][] ZZa, TZTZa;
			int[] index;
			Matrix ZTD, Z, T, Delta, DI, sumZ, sumZ_line, ZZ_line, ZZ;
			Matrix beta0, beta1, ZB, thetaZ, theta, thetaZtmp, thetatmp, Gv, G;
			Matrix TZTZ, TZTZ_line, TZTZtmp, neghessian;
			Vector< Matrix > ZZv, ZZtmp, thetaZZ, TZTZv;
			int m=-1, full_n=0, n, i, j, iter=0, k, ki;
			double epsilon = Math.pow(10.0, -6.0);
			int maxIteration=20;
			
			// configuration file and accessing data structures
			FileInputStream config_stream;
			DataInputStream config_in;
			BufferedReader config_br;
			String config_line;
			String[] line_contents;
			HashMap<String, String> config;
			String config_key, config_value;
			
			// read configuration info. from file client_config
			config_stream = new FileInputStream("client_config");
			config_in = new DataInputStream(config_stream);
			config_br = new BufferedReader(new InputStreamReader(config_in));
			
			// read configuration information and populate hash map config
			config = new HashMap<String, String>();
			while ((config_line = config_br.readLine()) != null) {
				line_contents = config_line.split("=");
				config_key = line_contents[0].trim();
				config_value = line_contents[1].trim();
				config.put(config_key, config_value);
			}
			
			// host name and port number
			String host = config.get("host");
			int port = Integer.parseInt(config.get("port"));
			Socket socket;			
			// input and output stream
			DataInputStream in;
			DataOutputStream out;
			
			// missing filename as argument
			if (args.length < 1) {
				System.out.println("ERROR: Please provide the data file as " +
								   "an argument.");
				System.exit(-1);
			}
			file_name = args[0];
			System.out.println("Using data file '" + file_name + "'.");
			
			// access the file
			file_stream = new FileInputStream(file_name);
			file_in = new DataInputStream(file_stream);
			file_br = new BufferedReader(new InputStreamReader(file_in));
			while ((file_line = file_br.readLine()) != null) {
				full_n = full_n + 1;
				line_tokens = file_line.split("\t");
				if (m == -1) {
					m = line_tokens.length - 2;
				}else if (m != line_tokens.length - 2) {
					System.out.println("ERROR: data file dimensions don't " +
									   "match on line " + full_n + ".");
					System.exit(-1);
				}
				ZTDrow = new Vector<Double>();	
				for (i = 0; i < line_tokens.length; i++) {
					ZTDrow.add(new Double(line_tokens[i]));					
				}
				ZTDv.add(ZTDrow);
		    }
			file_in.close();
			
			//sort original data ZTD
			ZTDa = two_dim_vec_to_arr(ZTDv);
			ArrayComparator comparator = new ArrayComparator(ZTDa);
			Arrays.sort(ZTDa, comparator);
			ZTD = new Matrix(ZTDa);
			Z = ZTD.getMatrix(0,full_n-1,0,m-1);
			T = ZTD.getMatrix(0,full_n-1,m,m);
			Delta = ZTD.getMatrix(0,full_n-1,m+1,m+1); 	
			
			//change T to avoid di=1			
			int count=0;
			double T_now=T.get(0,0);
			double T_not1=T_now;
			for (i=0; i<full_n; i++) {
				if(T.get(i,0)-T_now==0){
					count+=1;
				}else{
					if(count==1){							//if alone
						T.set(i-1,0,T_not1);
						T_now=T.get(i,0);
						if (i==1){							//if first alone
							T.set(i-1,0,T_now);	
							count=2;
						}
					}else{
						T_not1=T.get(i-1,0);
						T_now=T.get(i,0);
						count=1;	
						if(i==full_n-1)						//if last alone
							T.set(i,0,T_not1);
					}
				}
			}
			
			// connect to server and establish input and output streams
			socket = new Socket(host, port);
			System.out.println("Connected to '" + host + "' on port " + port +
							   ".");
			in = new DataInputStream(socket.getInputStream());
			out = new DataOutputStream(socket.getOutputStream());			
			
			//send T to server and get Tuniq back
			out.writeInt(full_n);
			for(i=0;i<full_n;i++){
				out.writeDouble(T.get(i,0));
			}
			n = in.readInt();
			Tuniq = new double[n];
			for(i=0;i<n;i++){
				Tuniq[i] = in.readDouble();
			}
			
			//calculate ZZ
			ZZa = new double[full_n][m][m];
			for (i=0; i<m; i++) {
				for (j=0; j<m; j++) {
					ZZ_line = Z.getMatrix(0,full_n-1,i,i).arrayTimes(Z.getMatrix(0,full_n-1,j,j)).copy();
					for(k=0;k<full_n;k++){
						ZZa[k][i][j]=ZZ_line.get(k,0);
					}
				}
			}
			ZZv = new Vector< Matrix >();
			for(k=0;k<full_n;k++){
				ZZ=new Matrix(ZZa[k]);
				ZZv.add(ZZ);
			}
			
			//calculate DI, sumZ, index		
			sumZ_line = new Matrix(1,m,0);
			sumZ = new Matrix(n,m,0);
			DIa = new double[n];
			index = new int[n];
			k=0;ki=0;
			index[0]=1;
			for (i=0; i<full_n; i++) {
				if(T.get(i,0)==Tuniq[k]){
					if (Delta.get(i,0)==1){
						DIa[k]+=1;
						sumZ_line.plusEquals(Z.getMatrix(i,i,0,m-1));
					}
					ki+=1;
				}else {
					sumZ.setMatrix(k,k,0,m-1,sumZ_line);
					sumZ_line = new Matrix(1,m,0);
					k+=1;i-=1;index[k]=ki;
				}
			}
			DI = new Matrix(DIa,DIa.length);
			//send sumZ and DI to server
			for(i=0;i<n;i++){
				for(j=0;j<m;j++){
					out.writeDouble(sumZ.get(i,j));
				}
			}
			
			for(i=0;i<n;i++){
				out.writeDouble(DI.get(i,0));
			}
			beta0 = new Matrix(m, 1, -1.0);
		    beta1 = new Matrix(m, 1, 0.0);
			iter=0;
			while (max_abs((beta1.minus(beta0)).getArray())>epsilon && iter<maxIteration) {	
				beta0 = beta1.copy();
				//calculate theta
				ZB = Z.times(beta0);
				exp(ZB.getArray());
				thetatmp = new Matrix(full_n,1,0);
				theta = new Matrix(n,1,0);
				thetatmp.set(full_n-1,0,ZB.get(full_n-1,0));
				for(i=full_n-2;i>=0;i--){
					thetatmp.set(i,0,(ZB.get(i,0)+thetatmp.get(i+1,0)));
				}
				for(i=0;i<n;i++){
					theta.set(i,0,thetatmp.get(index[i],0)); 
				}
				//calculate thetaZ
				thetaZtmp = new Matrix(full_n,m,0);
				thetaZ= new Matrix(n,m,0);
				for(i=0;i<full_n;i++){
					thetaZtmp.setMatrix(i,i,0,m-1,Z.getMatrix(i,i,0,m-1).times(ZB.get(i,0)));
				}
				for(i=full_n-2;i>=0;i--){
					thetaZtmp.setMatrix(i,i,0,m-1,thetaZtmp.getMatrix(i+1,i+1,0,m-1).plus(thetaZtmp.getMatrix(i,i,0,m-1)));
				}
				for(i=0;i<n;i++){
					thetaZ.setMatrix(i,i,0,m-1,thetaZtmp.getMatrix(index[i],index[i],0,m-1));
				}
				//send theta, thetaZ to calculate gradient
				for(i=0;i<n;i++){
					out.writeDouble(theta.get(i,0));
				}
				for(i=0;i<n;i++){
					for(j=0;j<m;j++){
						out.writeDouble(thetaZ.get(i,j));
					}
				}
				//caculate thetaZZ
				ZZtmp = new Vector< Matrix >();
				for(i=0;i<full_n;i++){
					ZZtmp.add(ZZv.get(i).times(ZB.get(i,0)));
				}
				for(i=full_n-2;i>=0;i--){
					ZZtmp.set(i,ZZtmp.get(i).plus(ZZtmp.get(i+1)));
				}
				thetaZZ = new Vector< Matrix >();
				for(i=0;i<n;i++){					
					thetaZZ.add(ZZtmp.get(index[i]));
				}
				//send thetaZZ to caculate hessian
				for(i=0;i<n;i++){
					for(j=0;j<m;j++){
						for(k=0;k<m;k++){
							out.writeDouble(thetaZZ.get(i).get(j,k));
						}
					}
				}
				//receive beta
				for(i=0;i<m;i++){
					beta1.set(i,0,in.readDouble());
				}
				System.out.println("beta at iteration"+iter);
				beta1.print(7,7);
				iter+=1;
			}			
		}catch (Exception e) {
			System.out.println(e);
			System.exit(-1);
		}
    }
	public static void exp(double[][] A) {
		int i,j;
		for (i = 0; i < A.length; i++) {
			for (j = 0; j < A[i].length; j++) {
				A[i][j] = Math.exp(A[i][j]);
			}
		}
	}
	public static double max_abs(double[][] matrix) {
		int i,j;
		boolean set = false;
		double max = 0;			
		// iterate through matrix
		for (i = 0; i < matrix.length; i++) {
			for (j = 0; j < matrix[i].length; j++) {
				// maintain absolute max number found
				if (!set) {
					max = Math.abs(matrix[i][j]);
					set = true;
				}else if (Math.abs(matrix[i][j]) > max) {
					max = Math.abs(matrix[i][j]);
				}
			}
		}
		return max;
	}
	/* Convert a 2D vector of Doubles into a 2D array of doubles. */
	public static double[][] two_dim_vec_to_arr(Vector< Vector<Double> >V) {
		// allocate part of the array
		double[][] A = new double[V.size()][];
		int i;
		
		// allocate and convert rows of the vector
		for (i = 0; i < V.size(); i++) {
			A[i] = one_dim_vec_to_arr(V.get(i));
		}
		
		// return 2D array
		return A;
	}	
	/* Convert a Vector of Doubles into an array of doubles. */
	public static double[] one_dim_vec_to_arr(Vector<Double> V) {
		int size = V.size();
		int i;
		double[] A = new double[size];
		
		for (i = 0; i < size; i++) {
			A[i] = (V.get(i)).doubleValue();
		}
		
		return A;
	}
}
class ArrayComparator implements Comparator<double[]>
{
	private final double[][] array;	
	public ArrayComparator(double[][] array){
		this.array = array;
	}
	@Override
	public int compare(double[] a, double[] b) {
		if(a[array[0].length-2] > b[array[0].length-2]) return 1;
		else return 0;
	}
}