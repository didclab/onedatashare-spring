import React, { Component } from 'react';
// ui import
import Card from '@material-ui/core/Card';
import CardContent from '@material-ui/core/CardContent';
import LinearProgress from '@material-ui/core/LinearProgress';
// components
import NewLoginComponent from './NewLoginComponent';
import SavedLoginComponent from './SavedLoginComponent';
import CreateAccountComponent from './CreateAccountComponent';
import ValidateEmailComponent from './ValidateEmailComponent';
import ForgotPasswordComponent from './ForgotPasswordComponent';

import { Route, Switch, Redirect } from 'react-router-dom';

import { login } from '../../APICalls/APICalls.js';

import { transferPageUrl } from "../../constants";
import { store } from '../../App.js';
import { loginAction } from '../../model/actions';
import {cookies} from '../../model/reducers';

export default class AccountControlComponent extends Component {

	constructor(props) {
		super(props);
    // redux login action
    this.unsubscribe = store.subscribe(() => {
    	this.setState({authenticated : store.getState().login});
    });


    const cookieSaved = cookies.get('SavedUsers') || 0;
    const accounts = cookieSaved === 0 ? {} : JSON.parse(cookieSaved);
    this.newLogin = <SavedLoginComponent 
					accounts={accounts} 
					login={(email) => {
						const user = JSON.parse(cookies.get('SavedUsers'))[email];
						this.userLogin(email, user.hash, false);
					}}
					removedAccount={(accounts) => {
						cookies.set('SavedUsers', JSON.stringify(accounts));
						this.setState({loading: false, accounts: accounts});
					}}
					useAnotherAccount={() => {
						this.setState({signIn: true});
					}}
					isLoading={(loading) => {
						this.setState({loading: loading});
					}}
				/>;
	this.state = {
    	isSmall: window.innerWidth <= 640,
    	password: "",
    	loading: true,
    	accounts: accounts,
    	authenticated: store.getState().login,
    	screen: this.newLogin,
    	creatingAccount: false,
    	loggingAccount: false,
    	signIn: false,
    	forgotPasswordPressed: false,
    	validateEmailPressed: false
		}
   	this.getInnerCard = this.getInnerCard.bind(this);
   	this.userLogin = this.userLogin.bind(this);
   	this.userSigningIn = this.userSigningIn.bind(this);
	}
	
	componentDidMount() {
		document.title = "OneDataShare - Account";
		window.addEventListener("resize", this.resize.bind(this));
		this.setState({ loading: false });
		this.resize();
	}

	static propTypes = {}
	
  // Called when user clicked login
  userLogin(email, hash, remember, saveOAuthTokens){
  	this.state.accounts[email] = { hash: hash };
	if(remember){
		cookies.set('SavedUsers', JSON.stringify(this.state.accounts));
	}
	
	store.dispatch(loginAction(email, hash, remember, saveOAuthTokens));
	//this.setState({authenticated : true});
  }
  componentWillUnmount(){
  	this.unsubscribe();
  }

	resize() {
		if (this.state.isSmall && window.innerWidth > 640) {
			this.setState({ isSmall: false });
		} else if (!this.state.isSmall && window.innerWidth <= 640) {
			this.setState({ isSmall: true });
		}
	}

	userSigningIn(email, password, remember, fail){
		login(email, password,
			(success) => {
				console.log("success account", success);
	    		this.userLogin(email, success.hash, remember, success.saveOAuthTokens);
	    	},
	    	(error) => {fail(error)}
	    );
	}
	
	getInnerCard() {
		return (
			<Switch>
				<Route exact path={'/account'}
					render={(props) => this.state.screen}>
				</Route>
				<Route exact path={'/account/register'}
					render={(props) => <CreateAccountComponent {...props}
						create={(email, password) => {

						}}
						backToSignin={() => {
							console.log("click");
							this.setState({ loggingAccount: true });
						}}
					/>}>
				</Route>
				<Route exact path={'/account/lostValidationCode'}
					render={(props) => <ValidateEmailComponent {...props}
						email={this.state.email}
						back={() => {
							this.setState({ loading: false, loggingAccount: true, validateEmailPressed: false });
						}} />}>
				</Route>

				<Route exact path={'/account/signIn'}
					render={(props) =>
						<div>
							{(this.state.forgotPasswordPressed || this.state.validateEmailPressed) &&
								<Redirect to='/account' />
							}
							<NewLoginComponent email={this.props.email}
								isLoading={(loading) => {
									this.setState({ loading: loading });
								}}

								createAccountPressed={() => {
									this.setState({ loading: false, createAccount: true, creatingAccount: true });
								}}

								validateEmailPressed={(email) => {
									this.setState({
										loading: false,
										validateEmailPressed: true,
										email: email
									});
								}}

								forgotPasswordPressed={(email) => {
									this.setState({
										loading: false, screen:
											<ForgotPasswordComponent back={() => {
												this.setState({ loading: false, screen: this.newLogin, forgotPasswordPressed: false });
											}} email={email} />,
										forgotPasswordPressed: true
									});
								}}

								userLoggedIn={this.userSigningIn}
							/>
						</div>
					}>
				</Route>
			</Switch>
		);
	}

	render() {

		const { isSmall, loading, creatingAccount, loggingAccount, signIn, validateEmailPressed } = this.state;
		const height = window.innerHeight + "px";

		return (

			<div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', width: '..', height: height }}>
				<div style={{ width: '450px', marginTop: '30px', marginLeft: '30px', marginRight: '30px', alignSelf: isSmall ? 'flex-start' : 'center' }}>

					{store.getState().login && <Redirect to={transferPageUrl} />}
					{creatingAccount && <Redirect to={"/account/register"} />}
					{validateEmailPressed && <Redirect to={{ pathname: "/account/lostValidationCode" }} />}
					{loggingAccount && <Redirect to={"/account"} />}
					{signIn && <Redirect to={"/account/signIn"} />}
					{loading && <LinearProgress />}

					{isSmall &&
						this.getInnerCard()
					}
					{!isSmall &&
						<Card>
							<CardContent style={{ padding: '3em' }}>
								{this.getInnerCard()}
							</CardContent>
						</Card>
					}
				</div>
			</div>

		);
	}
}