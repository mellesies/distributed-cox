############ Newton method for Cox proportional hazard model MLE based on Breslow's Partial likelihood #####################
# t is censored or uncensored time
# delta is indicator function, 1 means uncensored, 0 means censored
# z is the covariate matrix, row number is feature number 
# cumsum() rev() and apply() are used for matrix or array operations

full_data<-as.matrix(read.table("C:/Users/yuan/Dropbox/DistributedCOX/code/seer.txt",sep="\t"))	
full_n<-dim(full_data)[1]
add<-full_data[,1]

race<-full_data[,2]
race[race>=3&race<=97]<-3
race2<-rep(0,full_n)
race2[race==2]<-1
race3<-rep(0,full_n)
race3[race==3]<-1
race98<-rep(0,full_n)
race98[race==98]<-1
race99<-rep(0,full_n)
race99[race==99]<-1

marital<-full_data[,3]
mar2<-rep(0,full_n)
mar2[marital==2]<-1
mar3<-rep(0,full_n)
mar3[marital==3]<-1
mar4<-rep(0,full_n)
mar4[marital==4]<-1
mar5<-rep(0,full_n)
mar5[marital==5]<-1
mar9<-rep(0,full_n)
mar9[marital==9]<-1

histology<-full_data[,4]
hist8520<-rep(0,full_n)
hist8520[histology==8520]<-1
hist8522<-rep(0,full_n)
hist8522[histology==8522]<-1
hist8480<-rep(0,full_n)
hist8480[histology==8480]<-1
hist8501<-rep(0,full_n)
hist8501[histology==8501]<-1
hist8201<-rep(0,full_n)
hist8201[histology==8201]<-1
hist8211<-rep(0,full_n)
hist8211[histology==8211]<-1

grade<-full_data[,5]

ts<-full_data[,6]

nne<-full_data[,7]

npn<-full_data[,8]



er<-full_data[,12]
er2<-rep(0,full_n)
er2[er==2]<-1
er3<-rep(0,full_n)
er3[er==3]<-1
er4<-rep(0,full_n)
er4[er==4]<-1

t<-full_data[,9]+full_data[,10]*12
delta<-full_data[,11]
delta[delta==1]<-0
delta[delta==4]<-1

z<-cbind(add,race2,race3,mar2,mar3,mar4,mar5,mar9,hist8520,hist8522,hist8480,hist8501,hist8201,hist8211,grade,ts,nne,npn,er2,er4)

z<-z[race98==0&race99==0&er3==0,]
t<-t[race98==0&race99==0&er3==0]
delta<-delta[race98==0&race99==0&er3==0]

full_n<-dim(z)[1]


# sorting oberserved data according to accending order of t
z<-z[order(t),]
delta<-delta[order(t)]
t<-t[order(t)]

m<-dim(z)[2]
full_n<-length(t)

zz<-array(0,c(full_n,m,m))

for(i in 1:m)
{
	for(j in 1:m)
		{
			zz[,i,j]<-z[,i]*z[,j]
		}
}

# based on the partial likelihood function, newton method needs some quantities for all unique time points
unique_t<-unique(t)
n<-length(unique_t)
s<-matrix(0,n,m)
d<-rep(0,n)
index<-rep(0,n)

############# this part should be improved by dropping the loops#####################
for(i in 1:n)
{
    if (length(t[t==unique_t[i]&delta==1])>1)
		s[i,]<-colSums(z[t==unique_t[i]&delta==1,])
	if (length(t[t==unique_t[i]&delta==1])==1)
		s[i,]<-z[t==unique_t[i]&delta==1,]
	if (length(t[t==unique_t[i]&delta==1])==0)
	    s[i,]<-rep(0,m)
}

for(i in 1:n)
{
    d[i]<-length(t[t==unique_t[i]&delta==1])
}

for(i in 1:n)
{
	index[i]<-length(t[t<unique_t[i]])+1
}
########################################################################################
# newton method starts
beta_old<-matrix(rep(-1,m),m,1)
beta<-matrix(rep(0,m),m,1)
k<-0

while(abs(sum(beta-beta_old))>10^-6&k<20)
{
	beta_old<-beta
	temp1<-exp(z%*%beta_old)
	temp1<-c(temp1)
	temp2<-rev(temp1)
	temp2<-cumsum(temp2)
	temp2<-rev(temp2)

	sum_matrix<-z*temp1
	sum_matrix<-apply(sum_matrix,2,rev)
	sum_matrix<-apply(sum_matrix,2,cumsum)
	sum_matrix<-apply(sum_matrix,2,rev)
	sum_matrix<-sum_matrix/temp2
	sum_matrix<-sum_matrix[index,]

	gradient<-colSums(s-sum_matrix*d)
	
	# gradient for partial likelihood function
	gradient<-matrix(gradient,m,1)

	sum_array<-zz*temp1
	sum_array<-apply(sum_array,c(2,3),rev)
	sum_array<-apply(sum_array,c(2,3),cumsum)
	sum_array<-apply(sum_array,c(2,3),rev)
	sum_array<-sum_array/temp2
	sum_array<-sum_array[index,,]

	for(i in 1:m)
	{
		for(j in 1:m)
		{
			sum_array[,i,j]<-sum_array[,i,j]-sum_matrix[,i]*sum_matrix[,j]
		}
	}

	neghessian<-sum_array*d
	
	# Hessian matrix for partial likelihood function
	neghessian<-apply(neghessian,c(2,3),sum)
	# newton iteration
	beta<-beta_old+solve(neghessian+diag(10^(-6),m))%*%gradient
	k<-k+1
}

beta
# R package for Cox PH model
library(splines)
library(survival)
fit <- coxph(Surv(t, delta) ~ z[,1] +z[,2] +z[,3] +z[,4] +z[,5] +z[,6] +z[,7] +z[,8] +z[,9] +z[,10] +z[,11] +z[,12] +z[,13] +z[,14]  +z[,15] +z[,16] +z[,17] +z[,18]+z[,19] +z[,20],method="breslow")
